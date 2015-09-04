package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.{PositionUtil, Debug, TypeCheckUtil}

import scala.annotation.tailrec
import scala.collection.immutable.TreeSet
import scala.reflect.macros.blackbox.Context

private[dependencyLookup] class ValuesOfTypeInEnclosingClassFinder[C <: Context](val c: C, debug: Debug) {

  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)
  private val positionUtil = new PositionUtil[c.type](c)

  import typeCheckUtil._

  object ValDefOrDefDef {
    def unapply(t: Tree): Option[(TermName, Tree, Tree, Symbol)] = t match {
      case ValDef(_, name, tpt, rhs) => Some((name, tpt, rhs, t.symbol))
      case DefDef(_, name, _, _, tpt, rhs) => Some((name, tpt, rhs, t.symbol))
      case _ => None
    }
  }

  /**
   * @param implicitValue If a matching value will be found that has the same position as a matching implicit
   *                      value, the implicit value's tree will be used instead of a plain `Ident`, so that
   *                      it can be filter out later. (We still need to include it in the results, so that
   *                      we know that the search *did* find something and doesn't proceed to parents.)
   */
  def find(t: Type, implicitValue: Option[Tree] = None): List[Tree] = {

    // Find will look at:
    //   1. vals or defs
    //   2. imports
    //   3. members of vals whose type is annotated with @Module
    //
    // A problem arises when a member is found eligible by 2) and 3).
    // Here is an example:
    //
    //    class A
    //    class B(a: A)
    //
    //    @Module
    //    class M(val a: A)
    //
    //    class App(m: M) {
    //      import m.a
    //
    //      lazy val b = wire[B]
    //    }
    //
    // B requires an A, which is found twice:
    //   - by looking at the module: we find that `m` is a @Module and `m.a` is matching
    //   - by looking at the import: we find `a` as well
    //
    // To avoid that situation we will ignore duplicated matching trees, by using a Set AND always
    // referencing the enclosing class member (`m` in this case).
    // This way we won't end-up with `List(m.a, a)` but `Set(m.a)`.
    //
    // Now there's a bug in the compiler that prevent us from generating this:
    //
    //    class App(m: M) {
    //      import m.a
    //
    //      lazy val n = new B(m.a) // must be `new B(a)`
    //    }
    //
    // Trying to do so would crash the compiler:
    //
    //    symbol value plugin2#10075 does not exist in __wrapper$1$.__wrapper$1$ef$App$1.as$lzycompute,
    //    which contains locals variable monitor5#23346.
    //
    //    [...]
    //
    //    at scala.reflect.internal.Reporting$class.abort(Reporting.scala:59)
    //    at scala.reflect.internal.SymbolTable.abort(SymbolTable.scala:16)
    //    at scala.tools.nsc.backend.icode.GenICode$ICodePhase.genLoadIdent$1(GenICode.scala:890)
    //
    // This ticket https://issues.scala-lang.org/browse/SI-5797 seems related?
    // I'm too lazy right now to investigate further...
    //
    // Back to the ambiguity problem, if we can't use `m.a`, using a Set won't solve our problem as we will
    // end-up with `Set(m.a, a)` which bring us back to square one.
    //
    // As a workaround we'll use 2 kinds of candidate trees, one for comparing them (a witness)
    // and one that will be free of the bug above (the end-result).
    // So instead of having either `Set(m.a)` or `Set(m.a, a)`,
    // we'll have `Set(m.a)` (the witness) AND `Set(a)` (the end-result).
    //
    @tailrec
    def doFind(trees: List[Tree], witness: TreeSet[Tree], acc: List[Tree]): List[Tree] = {

      trees match {
        case Nil => acc
        case tree :: tail =>
          val (newWitness, newAcc) = evaluate(tree).foldLeft((witness,acc)) {
            case ((witness, acc), (treeWitness, treeToAdd)) =>
              if( witness.contains(treeWitness)) {
                (witness,acc)
              } else {
                (witness + treeWitness, treeToAdd :: acc)
              }
          }
          doFind(tail, newWitness, newAcc)
      }
    }

    /**
     * @return a List[(witnessTree, treeToAdd)]
     */
    def evaluate(tree: Tree) : List[(Tree,Tree)] = {
      tree match {
        case ValDefOrDefDef(name, tpt, rhs, symbol) if name.toString != "<init>" =>
          evaluateValOrDef(tree, name, tpt, rhs, symbol)

        case Import(expr, selectors) =>
          evaluateImport(tree, expr, selectors)

        case _ => Nil
      }
    }

    def evaluateImport(tree: c.universe.Tree, expr: c.universe.Tree, selectors: List[c.universe.ImportSelector]): List[(c.universe.Tree, c.universe.Tree)] = {
      val matches = debug.withBlock(s"Looking up imports in [$tree]") {

        val importCandidates: List[(Symbol, (Tree, Tree))] =
          (if (selectors.exists { selector => selector.name.toString == "_" }) {
            // wildcard import on `expr`
            typeCheckIfNeeded(expr).members.filter {
              _.isPublic
            }.map {
              s => s -> s.name.decodedName
            }
          } else {
            val selectorNames = selectors.map(s => s.name -> s.rename).toMap
            typeCheckIfNeeded(expr).
              members.
              collect { case m if selectorNames.contains(m.name) =>
              m -> selectorNames(m.name)
            }
          }).map { case (s, name) => (s, (q"$expr.$s", Ident(name))) }.toList


        filterImportMembers(importCandidates)
      }
      matches
    }

    def evaluateValOrDef(tree: c.universe.Tree, name: c.universe.TermName, tpt: c.universe.Tree, rhs: c.universe.Tree, symbol: c.universe.Symbol): List[(c.universe.Tree, c.universe.Tree)] = {
      val candidateOk = checkCandidate(t, name, tpt, treeToCheck(tree, rhs),
        if (symbol.isMethod) "def" else "val")

      if (candidateOk) {
        val treeToAdd = implicitValue match {
          case Some(i) if positionUtil.samePosition(i.symbol.pos, symbol.pos) => i
          case _ => Ident(name)
        }

        List(treeToAdd -> treeToAdd)
      } else {

        // it might be a @Module, let's see
        val hasSymbol = tpt.symbol != null // sometimes tpt has no symbol...
        val valIsModule = hasSymbol && hasModuleAnnotation(tpt.symbol)
        // the java @Inherited meta-annotation does not seem to be understood by scala-reflect...
        val valParentIsModule = hasSymbol && !valIsModule && typeCheckIfNeeded(tpt).baseClasses.exists(hasModuleAnnotation)

        if (valIsModule || valParentIsModule) {
          val matches = debug.withBlock(s"$name is a module of type $tpt, looking up its members") {
            typeCheckIfNeeded(tpt).members.filter(filterMember(_, ignoreImplicit = false)).map { member =>
              q"$name.$member"
            }.toList
          }
          matches.map { m => m -> m }
        } else {
          Nil
        }
      }
    }

    def hasModuleAnnotation(symbol: Symbol) : Boolean = {
      symbol.annotations.exists { annotation =>
        annotation.tree match {
          case q"new $parent()" => parent.symbol.fullName == "com.softwaremill.macwire.Module"
          case _ => false
        }
      }
    }

    def filterImportMembers[T](members: List[(Symbol,T)]) : List[T] = {
      members.collect { case (m,t) if filterMember(m, ignoreImplicit = true) => t }
    }

    def filterMember(member: Symbol, ignoreImplicit: Boolean) : Boolean = {
      if( member.fullName.startsWith("java.lang.Object") ||
          member.fullName.startsWith("scala.Any") ) {
        false
      } else {
        debug.withBlock(s"Checking [$member]") {
          if (!member.isPublic) {
            false
          } else if (ignoreImplicit && member.isImplicit) {
            // ignore implicits as they will be picked by `ImplicitValueOfTypeFinder`
            debug("Ignoring implicit (will be picked later on)")
            false
          } else {
            val ok = checkCandidate(target = t, tpt = member.typeSignature)
            if (ok) debug("Found a match!")
            ok
          }
        }
      }
    }

    def treeToCheck(tree: Tree, rhs: Tree) = {
      // If possible, we check the definition (rhs). We can't always check the tree, as it would cause recursive
      // type ascription needed errors from the compiler.
      if (rhs.isEmpty) tree else rhs
    }

    val enclosingClassBody = c.enclosingClass match {
      case ClassDef(_, _, _, Template(_, _, body)) => body
      case ModuleDef(_, _, Template(_, _, body)) => body
      case e =>
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
    }

    debug("Looking in the enclosing class/trait")

    // Create a special Set that uses structural equality on Tree;
    // `equals` on trees is reference equality.
    val emptyTreeSet = TreeSet.empty(new Ordering[Tree] {
      override def compare(x: c.universe.Tree, y: c.universe.Tree): Int = {
        if (x.equalsStructure(y)) 0
        else x.hashCode().compareTo(y.hashCode()) // some arbitrary comparison that should retain the compare semantic
      }
    })

    doFind(enclosingClassBody, emptyTreeSet, Nil)
  }
}
