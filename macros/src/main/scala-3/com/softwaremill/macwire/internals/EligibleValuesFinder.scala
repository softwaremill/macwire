package com.softwaremill.macwire.internals

import scala.annotation.tailrec
import scala.quoted.*

private[macwire] class EligibleValuesFinder[Q <: Quotes](log: Logger)(using val q: Q) {

  import q.reflect.*
  import EligibleValuesFinder.*

    private val typeCheckUtil = new TypeCheckUtil[q.type](log)
  import typeCheckUtil._

  def find(): EligibleValues = {
    val wiredDef = Symbol.spliceOwner.owner
    val wiredOwner = wiredDef.owner
    
    /** TODO
    * support for multilevel enclosing class and parameters - diamondInheritance.success,implicitDepsWiredWithImplicitDefs.success, implicitDepsWiredWithImplicitVals.success
    * support for method params - implicitDepsWiredWithImplicitValsFromMethodScope.success, functionApplication.success,anonFuncAndMethodsArgsWiredOk.success, anonFuncArgsWiredOk.success,
    *    methodMixedOk.success, methodParamsInApplyOk.success, methodParamsOk.success, methodSingleParamOk.success, methodWithSingleImplicitParamOk.success, nestedAnonFuncsWired.success
    *    nestedMethodsWired.success
    * search in blocks - methodContainingValDef.success, methodWithWiredWithinIfThenElse.success, methodWithWiredWithinPatternMatch.success, 
    **/

    //1 - enclosing class
    val classScopeValues = (wiredOwner.declaredMethods ::: wiredOwner.declaredFields)
        .filter(m => !m.fullName.startsWith("java.lang.Object") && !m.fullName.startsWith("scala.Any"))
        .map(_.tree).collect {
            case m: ValDef => EligibleValue(m.rhs.map(_.tpe).getOrElse(m.tpt.tpe), m)
            case m: DefDef if m.termParamss.flatMap(_.params).isEmpty =>
                EligibleValue(m.rhs.map(_.tpe).getOrElse(m.returnTpt.tpe), m)
    }

    //2 - imported instances 
    //TODO
    //it seems that import statement is missed in the tree obtained from Symbol.spliceOwner
    //https://github.com/lampepfl/dotty/issues/12965
    //Tests: import*.success (7)
    val importScopeValues = List.empty[EligibleValue]

    //3 - parent types
    // val parentScopValues = (wiredOwner.memberMethods ::: wiredOwner.memberFields).filterNot((wiredOwner.declaredMethods ::: wiredOwner.declaredFields).toSet)
    //     .filter(m => !m.fullName.startsWith("java.lang.Object") && !m.fullName.startsWith("scala.Any"))
    //     .map(_.tree).collect {
    //         case m: ValDef => EligibleValue(m.rhs.map(_.tpe).getOrElse(m.tpt.tpe), m)
    //         case m: DefDef if m.termParamss.flatMap(_.params).isEmpty =>
    //             EligibleValue(m.rhs.map(_.tpe).getOrElse(m.returnTpt.tpe), m)
    // }
    // https://github.com/lampepfl/dotty/discussions/12966
    //Tests: implicitDepsWiredWithImplicitValsFromParentsScope.success, inheritance*.success(9), selfType.success, selfTypeHKT.success
    val parentScopValues = List.empty[EligibleValue]

    EligibleValues(Map(
        Scope.Class -> classScopeValues,
        Scope.ParentOrModule -> importScopeValues,
        Scope.ModuleInParent -> parentScopValues
    ))
  }

  case class EligibleValue(tpe: TypeRepr, expr: Tree) {
    // equal trees should have equal hash codes; if trees are equal structurally they should have the same toString?
    override def hashCode() = expr.toString().hashCode

    override def equals(obj: scala.Any) = obj match {
      case EligibleValue(_, e) => expr == e//FIXME not sure if `equalsStructure` -> `==` 
      case _ => false
    }
  }

  class EligibleValues(values: Map[Scope, List[EligibleValue]]) {

    private def doFindInScope(tpe: TypeRepr, scope: Scope): List[Tree] = {
      for( scopedValue <- values.getOrElse(scope, Nil) if checkCandidate(target = tpe, tpt = scopedValue.tpe)) yield {
        scopedValue.expr
      }
    }

    private def uniqueTrees(trees: List[Tree]): Iterable[Tree] = {
      // the only reliable method to compare trees is using structural equality, but there shouldn't be a lot of
      // trees with a given type, so the n^2 complexity shouldn't hurt
      def addIfUnique(addTo: List[Tree], t: Tree): List[Tree] = {
        addTo.find(_ == t).fold(t :: addTo)(_ => addTo)
      }

      trees.foldLeft(List.empty[Tree])(addIfUnique)
    }

    def findInScope(tpe: TypeRepr, scope: Scope): Iterable[Tree] = {
      uniqueTrees(doFindInScope(tpe, scope))
    }

    def findInFirstScope(tpe: TypeRepr, startingWith: Scope = Scope.Local): Iterable[Tree] = {
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

    def findInAllScope(tpe: TypeRepr): Iterable[Tree] = {
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
