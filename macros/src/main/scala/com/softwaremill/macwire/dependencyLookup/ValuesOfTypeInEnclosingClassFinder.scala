package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.{PositionUtil, Debug, TypeCheckUtil}

import scala.annotation.tailrec
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

    @tailrec
    def doFind(trees: List[Tree], acc: List[Tree]): List[Tree] = {
      trees match {
        case Nil => acc
        case tree :: tail => tree match {
          case ValDefOrDefDef(name, tpt, rhs, symbol) =>
            val candidateOk = checkCandidate(t, name, tpt, treeToCheck(tree, rhs),
              if (symbol.isMethod) "def" else "val")

            if (candidateOk) {
              val treeToAdd = implicitValue match {
                case Some(i) if positionUtil.samePosition(i.symbol.pos, symbol.pos) => i
                case _ => Ident(name)
              }

              doFind(tail, treeToAdd :: acc)
            } else {

              // it might be a @Module, let's see
              val hasSymbol = tpt.symbol != null // sometimes tpt has no symbol...
              val valIsModule = hasSymbol && tpt.symbol.annotations.exists { annotation =>
                  annotation.tree match {
                    case q"new $parent()" => parent.symbol.fullName == "com.softwaremill.macwire.Module"
                    case _ => false
                  }
                }

              if (valIsModule) {
                val matches = debug.withBlock(s"Looking up members of module $tpt") {
                  typeCheckIfNeeded(tpt).members.filter(filterMember(_,ignoreImplicit = false)).map { member =>
                    q"$name.$member"
                  }.toList
                }
                doFind(tail, matches ::: acc)
              } else {
                doFind(tail, acc)
              }
            }

          case Import(expr, selectors) =>
            val matches = debug.withBlock(s"Looking up imports in [$tree]") {

              val importCandidates : Map[Symbol, Name] =
                if (selectors.exists { selector => selector.name.toString == "_" }) {
                  // wildcard import on `expr`
                  typeCheckIfNeeded(expr).members.map {
                    s => s -> s.name.decodedName }.toMap
                } else {
                  val selectorNames = selectors.map(s => s.name -> s.rename).toMap
                  typeCheckIfNeeded(expr).members.
                    collect { case m if selectorNames.contains(m.name) =>
                      m -> selectorNames(m.name) }.
                    toMap
                }

              filterImportMembers(importCandidates)
            }
            doFind(tail, matches ::: acc)

          case _ => doFind(tail, acc)
        }
      }
    }

    def filterImportMembers(members: Map[Symbol, Name]) : List[Tree] = {
      members.
        filter { case (m,_) => filterMember(m, ignoreImplicit = true) }.
        map { case (_,name) => Ident(name) }.
        toList
    }

    def filterMember(member: Symbol, ignoreImplicit: Boolean) : Boolean = {
      debug.withBlock(s"Checking [$member]") {
        if( !member.isPublic ) {
          false
        } else if( ignoreImplicit && member.isImplicit ) {
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
    doFind(enclosingClassBody, Nil)
  }
}
