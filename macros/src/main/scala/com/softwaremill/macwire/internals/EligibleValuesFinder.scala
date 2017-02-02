package com.softwaremill.macwire.internals

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

private[macwire] class EligibleValuesFinder[C <: blackbox.Context](val c: C, log: Logger) {

  import c.universe.{Scope => RScope, _}
  import EligibleValuesFinder._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
  import typeCheckUtil._

  def find(): EligibleValues = {
    def containsCurrentlyExpandedWireCall(t: Tree): Boolean = t.exists(_.pos == c.enclosingPosition)

    @tailrec
    def doFind(trees: List[(Scope, Tree)], values: EligibleValues): EligibleValues = trees match {
      case Nil => values
      case (scope, tree) :: tail => tree match {

        case _ if containsCurrentlyExpandedWireCall(tree) =>
          val (treesToAdd, forwardTrees, newValues) = tree match {

            // Look into things like
            //    `x.map { ... wire[Y] ... }`
            case Apply(_, args) =>
              (args, Nil, values)

            case Block(statements, expr) =>
              // the statements might contain vals, defs, or imports which will be
              // analyzed in the match clauses below (see `case ValDefOrDefDef`)
              val (before,after) = partitionStatementsAfterWireCall(statements :+ expr)
              (before, after, values)

            case ValDef(_, name, _, rhs) =>
              (List(rhs), Nil, values)

            case DefDef(_, name, _, curriedParams, tpt, rhs) =>
              log.withBlock(s"Inspecting the parameters of method $name") {
                (List(rhs), Nil, values.putAll(Scope.Local, extractMatchingParams(curriedParams.flatten)))
              }

            case Function(params, body) =>
              log.withBlock("Inspecting a function that contains the wire call") {
                (List(body), Nil, values.putAll(Scope.Local, extractMatchingParams(params)))
              }

            case ifBlock@If(cond, then, otherwise) =>
              (List(then, otherwise), Nil, values)

            case Match(_, cases) =>
              (cases, Nil, values)

            // Looking into things like
            //     `{ case Deconstruct(x,y) => ... wire[Z] ... }`
            // Note that `x` and `y` will be analyzed in the `Bind` case below
            case CaseDef(Apply(_, args), _, body) =>
              (args ++ List(body), Nil, values)

            // Looking into things like
            //     `{ case x => ... wire[Y] ... }`
            // Note that `x` will be analyzed in the `Bind` case below
            case CaseDef(pat, _, body) =>
              (List(pat, body), Nil, values)

            case _ =>
              (Nil, Nil, values)
          }
          // we're in a block that contains the wire call, therefore we're looking at the smallest scope, Local
          doFind(treesToAdd.map(Scope.Local -> _) :::
                 forwardTrees.map(Scope.LocalForward -> _) :::
                 tail, newValues)

        case Bind(name, body) =>
          doFind(tail, values.put(scope, Ident(name), body))

        case ValDefOrEmptyDefDef(name, tpt, rhs, symbol) if name.toString != "<init>" =>
          // check if annotated type is different from actual
          val expr = Ident(name)
          val (theTreeToCheck, lhsTpt) = if (tpt.tpe == rhs.tpe) {
            // rhs might be empty for local def
            (treeToCheck(tree, rhs), Some(tpt))
          } else {
            (treeToCheck(tree, tpt), None)
          }
          doFind(tail, values.put(scope, expr, theTreeToCheck, lhsTpt))

        case Import(expr, selectors) =>
          val newValues = if( expr.symbol.isPackage ) {
            // just ignore package imports
            values
          } else {
            log.withBlock("Inspecting imports"){
              val importCandidates: List[(Symbol, Tree)] =
                (if (selectors.exists { selector => selector.name.toString == "_" }) {
                  // wildcard import on `expr`
                  typeCheckIfNeeded(expr).members.map {
                    s => s -> s.name.decodedName
                  }
                } else {
                  val selectorNames = selectors.map(s => s.name -> s.rename).toMap
                  typeCheckIfNeeded(expr).
                    members.
                    collect { case m if selectorNames.contains(m.name) =>
                    m -> selectorNames(m.name)
                  }
                }).map { case (s, name) => (s, Ident(name)) }.toList
              values.putAll(scope, filterImportMembers(importCandidates).map(t => (t,t)))
            }
          }

          doFind(tail, newValues)

        case _ =>
          doFind(tail, values)
      }
    }

    log.withBlock("Building eligible values") {
      registerParentsMembers(
        doFind(enclosingClassBody.map(Scope.Class -> _), EligibleValues.empty))
    }
  }

  /** @return all the members of all the parents */
  private def registerParentsMembers(values: EligibleValues): EligibleValues = {
    val parents = c.enclosingClass match {
      case ClassDef(_, _, _, Template(pp, self, _)) =>
        val selfTypes = self.tpt match {
          case ident : Ident => List(ident)
          case CompoundTypeTree(Template(selfParents,_,_)) => selfParents
          case x : Select if x.isType => List(x)
          case _ => Nil
        }
        pp ++ selfTypes

      case ModuleDef(_, _, Template(pp, _, _)) => pp
      case e =>
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
    }

    parents.foldLeft(values) { case (newValues,parent) =>
      val tpe: Tree = parent match {
        case q"$tpe(..$params)" => tpe // ignore parameters passed to the parent
        case q"$tpe" => tpe
      }
      if (tpe.symbol.fullName == "scala.AnyRef") {
        newValues
      } else {
        log.withBlock(s"Inspecting parent $tpe members") {
          typeCheckIfNeeded(tpe).members.
            filter(filterMember).
            foldLeft(newValues) { case (newValues, symbol) =>
            newValues.put(Scope.ParentOrModule, symbol.typeSignature,
              Ident(TermName(symbol.name.decodedName.toString.trim()))) // q"$symbol" crashes the compiler...
          }
        }
      }
    }
  }

  private def hasModuleAnnotation(symbol: Symbol) : Boolean = {
    symbol.annotations.exists { annotation =>
      annotation.tree match {
        case q"new $parent()" => parent.symbol.fullName == "com.softwaremill.macwire.Module"
        case _ => false
      }
    }
  }

  /** @return (statements-before-wire, statements-after-wire) */
  private def partitionStatementsAfterWireCall(statements: List[Tree]): (List[Tree], List[Tree]) = {
    statements.partition { _.pos.end <= c.enclosingPosition.start }
  }

  private def filterImportMembers[T](members: List[(Symbol,T)]) : List[T] = {
    members.collect { case (m,t) if filterMember(m) => t }
  }

  private def filterMember(member: Symbol) : Boolean = {
    !member.fullName.startsWith("java.lang.Object") &&
    !member.fullName.startsWith("scala.Any") &&
    !member.fullName.endsWith("<init>") &&
    !member.fullName.endsWith("$init$") &&
    member.isPublic
  }

  private def treeToCheck(tree: Tree, rhs: Tree) = {
    // If possible, we check the definition (rhs). We can't always check the tree, as it would cause recursive
    // type ascription needed errors from the compiler.
    if (rhs.isEmpty) tree else rhs
  }

  private def extractMatchingParams(params: List[ValDef]): List[(Tree,Tree)] = params.collect {
    case param@ValDef(_, name, tpt, _) => (Ident(name), treeToCheck(param, tpt))
  }

  case class EligibleValue(tpe: Type, expr: Tree) {
    // equal trees should have equal hash codes; if trees are equal structurally they should have the same toString?
    override def hashCode() = expr.toString().hashCode

    override def equals(obj: scala.Any) = obj match {
      case EligibleValue(_, e) => expr.equalsStructure(e)
      case _ => false
    }
  }

  class EligibleValues(values: Map[Scope, List[EligibleValue]]) {

    /** Add all `exprs` to `scope` and possibly their respective members if they denote a module */
    def putAll(scope: Scope, exprs: List[(Tree,Tree)]): EligibleValues = {
      exprs.foldLeft(this) {
        case (ev, (expr,tree)) => ev.put(scope, expr, tree)
      }
    }

    /** Add `expr` to `scope` and possibly its members if it denotes a module */
    def put(scope: Scope, expr: Tree, tree: Tree, lhsTpt: Option[Tree] = None): EligibleValues = {
      val tpe = lhsTpt
        .map(typeCheckUtil.typeCheckIfNeeded)
        .filterNot(typeOf[Any] =:= _)
        .getOrElse(typeCheckUtil.typeCheckIfNeeded(expr, tree))
      put(scope, tpe, expr)
    }

    /** Add `expr` to `scope` and possibly its members if it denotes a module */
    def put(scope: Scope, tpe: Type, expr: Tree): EligibleValues = {
      doPut(scope, tpe, expr).
        inspectModule(scope, tpe, expr)
    }

    private def doPut(scope: Scope, tpe: Type, expr: Tree): EligibleValues = {
      log(s"Found $expr of type $tpe in scope $scope")
      val set = EligibleValue(tpe, expr) :: values.getOrElse(scope, Nil)
      new EligibleValues(values.updated(scope, set))
    }

    private def inspectModule(scope: Scope, tpe: Type, expr: Tree): EligibleValues = {
      // it might be a @Module, let's see
      val hasSymbol = expr.symbol != null // sometimes expr has no symbol...
      val valIsModule = hasSymbol && hasModuleAnnotation(expr.symbol)
      // the java @Inherited meta-annotation does not seem to be understood by scala-reflect...
      val valParentIsModule = hasSymbol && !valIsModule && tpe.baseClasses.exists(hasModuleAnnotation)

      if (valIsModule || valParentIsModule) {
        log.withBlock(s"Inspecting module $tpe") {
          val moduleExprs: List[(Tree,Tree)] = tpe.members.filter(filterMember).map { member =>
            val tree = q"$expr.$member"
            (tree,tree)
          }.toList
          // the module members are put in the wider scope
          putAll(scope.widen, moduleExprs)
        }
      } else {
        this // unchanged
      }
    }

    private def doFindInScope(tpe: Type, scope: Scope): List[Tree] = {
      for( scopedValue <- values.getOrElse(scope, Nil) if checkCandidate(target = tpe, tpt = scopedValue.tpe)) yield {
        scopedValue.expr
      }
    }

    private def uniqueTrees(trees: List[Tree]): Iterable[Tree] = {
      // the only reliable method to compare trees is using structural equality, but there shouldn't be a lot of
      // trees with a given type, so the n^2 complexity shouldn't hurt
      def addIfUnique(addTo: List[Tree], t: Tree): List[Tree] = {
        addTo.find(_.equalsStructure(t)).fold(t :: addTo)(_ => addTo)
      }

      trees.foldLeft(List.empty[Tree])(addIfUnique)
    }

    def findInScope(tpe: Type, scope: Scope): Iterable[Tree] = {
      uniqueTrees(doFindInScope(tpe, scope))
    }

    def findInFirstScope(tpe: Type, startingWith: Scope = Scope.Local): Iterable[Tree] = {
      @tailrec
      def forScope(scope: Scope): Iterable[Tree] = {
        findInScope(tpe, scope) match {
          case coll if coll.isEmpty && !scope.isMax => forScope(scope.widen)
          case coll if coll.isEmpty => log(s"Could not find $tpe in any scope"); Nil
          case exprs =>
            log(s"Found [${exprs.mkString(", ")}] of type [$tpe] in scope $scope")
            exprs
        }
      }
      forScope(startingWith)
    }

    def findInAllScope(tpe: Type): Iterable[Tree] = {
      @tailrec
      def accInScope(scope: Scope, acc: List[Tree]): List[Tree] = {
        val newAcc = doFindInScope(tpe, scope) ++ acc
        if( !scope.isMax ) accInScope(scope.widen, newAcc) else newAcc
      }
      uniqueTrees(accInScope(Scope.Local, Nil))
    }
  }

  object EligibleValues {
    val empty: EligibleValues = new EligibleValues(Map.empty)
  }

  object ValDefOrEmptyDefDef {
    def unapply(t: Tree): Option[(TermName, Tree, Tree, Symbol)] = t match {
      case ValDef(_, name, tpt, rhs) => Some((name, tpt, rhs, t.symbol))
      case DefDef(_, name, _, Nil, tpt, rhs) => Some((name, tpt, rhs, t.symbol))
      case _ => None
    }
  }

  /** @return Nil if no body can be found */
  private def enclosingClassBody: List[Tree] = c.enclosingClass match {
    case ClassDef(_, _, _, Template(_, _, body)) => body
    case ModuleDef(_, _, Template(_, _, body)) => body
    case e =>
      c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
      Nil
  }
}

object EligibleValuesFinder {
  abstract class Scope private(val value: Int) extends Ordered[Scope] {
    /** @return the next Scope until Max */
    def widen: Scope

    def isMax: Boolean = widen == this
    override def compare(that: Scope): Int = this.value.compare(that.value)
    override def equals(other: Any): Boolean = other match {
      case otherScope: Scope => this.value == otherScope.value
      case _ => false
    }
    override def hashCode = value.hashCode
  }

  object Scope extends Ordering[Scope] {

    /** The smallest Scope */
    case object Local extends Scope(1) {
      def widen: Scope = Class
    }
    case object Class extends Scope(2) {
      def widen: Scope = ParentOrModule
    }
    case object ParentOrModule extends Scope(3) {
      def widen: Scope = ModuleInParent
    }
    case object ModuleInParent extends Scope(4) {
      def widen: Scope = ModuleInParent
    }

    /** A special scope for values that are located in a block after the wire call
      * and therefore not reachable. */
    case object LocalForward extends Scope(9) {
      def widen: Scope = LocalForward
    }

    override def compare(a: Scope, b: Scope): Int = a.compare(b)
  }
}
