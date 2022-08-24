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

    def doFind(symbol: Symbol, scope: Scope): EligibleValues = {
      def handleClassDef(scope: Scope, s: Symbol): EligibleValues = {
        val declared = EligibleValues.build(
          symbol,
          scope,
          s.declarations.filter(nonSyntethic).map(_.tree)
        )
        val inherited = EligibleValues.build(
          symbol,
          scope.widen,
          (s.memberFields ::: s.memberMethods)
            .filter(m => nonSyntethic(m) && isPublic(m) && !s.declarations.contains(m))
            .map(_.tree)
        )
        EligibleValues.merge(declared, inherited)
      }

      def handleDefDef(scope: Scope, s: Symbol): EligibleValues =
        s.tree match {
          case DefDef(_, _, _, Some(Match(_, cases))) =>
            report.throwError(s"Wire for deconstructed case is not supported yet") // TODO
          case DefDef(s, lpc, tt, ot) =>
            EligibleValues.build(symbol, scope, lpc.flatMap(_.params))
        }

      if symbol.isNoSymbol then EligibleValues.empty
      else if symbol.isDefDef then EligibleValues.merge(handleDefDef(scope, symbol), doFind(symbol.maybeOwner, scope))
      else if symbol.isClassDef && !symbol.isPackageDef then handleClassDef(scope.widen, symbol)
      else if symbol == defn.RootPackage then EligibleValues.empty
      else if symbol == defn.RootClass then EligibleValues.empty
      else doFind(symbol.maybeOwner, scope.widen)
    }

    doFind(Symbol.spliceOwner, Scope.init)
  }

  private def nonSyntethic(member: Symbol): Boolean = {
    !member.fullName.startsWith("java.lang.Object") &&
    !member.fullName.startsWith("scala.Any") &&
    !member.fullName.startsWith("scala.AnyRef") &&
    !member.fullName.endsWith("<init>") &&
    !member.fullName.endsWith("$init$") &&
    !member.fullName.contains("$default$") && // default params for copy on case classes
    !member.fullName.matches(".*_\\d+") // tuple methods on case classes
  }

  private def isPublic(member: Symbol): Boolean = {
    !((member.flags is Flags.Private) || (member.flags is Flags.Protected))
  }

  case class EligibleValue(tpe: TypeRepr, expr: Tree) {
    // equal trees should have equal hash codes; if trees are equal structurally they should have the same toString?
    override def hashCode() = expr.toString().hashCode

    override def equals(obj: scala.Any) = obj match {
      case EligibleValue(_, e) => expr == e // FIXME not sure if `equalsStructure` -> `==`
      case _                   => false
    }
  }

  class EligibleValues(val values: Map[Scope, List[EligibleValue]]) {
    private lazy val maxScope = values.keys.maxOption.getOrElse(Scope.init)
    extension (scope: Scope) def isMax = scope == maxScope

    private def doFindInScope(tpe: TypeRepr, scope: Scope): List[Tree] = {
      for (scopedValue <- values.getOrElse(scope, Nil) if checkCandidate(target = tpe, tpt = scopedValue.tpe)) yield {
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

    def findInFirstScope(tpe: TypeRepr, startingWith: Scope = Scope.init): Iterable[Tree] = {
      @tailrec
      def forScope(scope: Scope): Iterable[Tree] = {
        findInScope(tpe, scope) match {
          case coll if coll.isEmpty && !scope.isMax => forScope(scope.widen)
          case coll if coll.isEmpty                 => log(s"Could not find $tpe in any scope"); Nil
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
        if (!scope.isMax) accInScope(scope.widen, newAcc) else newAcc
      }
      uniqueTrees(accInScope(Scope.init, Nil))
    }
  }

  object EligibleValues {
    val empty: EligibleValues = new EligibleValues(Map.empty)

    private def inspectModule(scope: Scope, tpe: TypeRepr, expr: Tree) = {
      def hasModuleAnnotation(symbol: Symbol): Boolean =
        symbol.annotations.map(_.tpe.show).exists(_ == "com.softwaremill.macwire.Module")

      // it might be a @Module, let's see
      val hasSymbol = expr.symbol != null // sometimes expr has no symbol...
      val valIsModule = hasSymbol && hasModuleAnnotation(expr.symbol)
      // the java @Inherited meta-annotation does not seem to be understood by scala-reflect...
      val valParentIsModule = hasSymbol && !valIsModule && tpe.baseClasses.exists(hasModuleAnnotation)

      if (valIsModule || valParentIsModule) {
        log.withBlock(s"Inspecting module $tpe") {
          val r = (expr.symbol.memberFields ::: expr.symbol.memberMethods)
            .filter(m => nonSyntethic(m) && isPublic(m))
            .map(_.tree)
            .collect {
              case m: ValDef =>
                EligibleValue(
                  m.tpt.tpe,
                  This(expr.symbol.owner).select(expr.symbol).select(m.symbol)
                )
              case m: DefDef if m.termParamss.flatMap(_.params).isEmpty =>
                EligibleValue(
                  m.rhs.map(_.tpe).getOrElse(m.returnTpt.tpe),
                  This(expr.symbol.owner).select(expr.symbol).select(m.symbol)
                )
            }
          EligibleValues(Map(scope.widen -> r))
        }
      } else {
        EligibleValues.empty
      }
    }

    private def buildEligibleValue(symbol: Symbol, scope: Scope): PartialFunction[Tree, EligibleValues] = {
      case m: ValDef =>
        merge(
          inspectModule(scope.widen, m.rhs.map(_.tpe).getOrElse(m.tpt.tpe), m),
          EligibleValues(
            Map(
              scope -> List(
                EligibleValue(
                  m.rhs.map(_.tpe).getOrElse(m.tpt.tpe),
                  if symbol.isClassDef then This(symbol).select(m.symbol) else m
                )
              )
            )
          )
        )
      case m: DefDef if m.termParamss.flatMap(_.params).isEmpty =>
        merge(
          inspectModule(scope.widen, m.rhs.map(_.tpe).getOrElse(m.returnTpt.tpe), m),
          EligibleValues(
            Map(
              scope -> List(
                EligibleValue(
                  m.rhs.map(_.tpe).getOrElse(m.returnTpt.tpe),
                  if symbol.isClassDef then This(symbol).select(m.symbol) else m
                )
              )
            )
          )
        )
    }

    def build(symbol: Symbol, scope: Scope, l: Iterable[Tree]) = l.foldLeft(empty)((values, tree) =>
      merge(values, buildEligibleValue(symbol, scope).applyOrElse(tree, _ => EligibleValues.empty))
    )

    def merge(ev1: EligibleValues, ev2: EligibleValues): EligibleValues =
      EligibleValues((ev1.values.toSeq ++ ev2.values.toSeq).groupBy(_._1).view.mapValues(_.flatMap(_._2).toList).toMap)
  }
}

object EligibleValuesFinder {
  case class Scope(val value: Int) extends Ordered[Scope] {

    /** @return the next Scope until Max */
    def widen: Scope = copy(value = this.value + 1)

    override def compare(that: Scope): Int = this.value.compare(that.value)
    override def equals(other: Any): Boolean = other match {
      case otherScope: Scope => this.value == otherScope.value
      case _                 => false
    }
    override def hashCode = value.hashCode
  }

  object Scope extends Ordering[Scope] {

    /** The smallest Scope */
    val init = Scope(1)

    override def compare(a: Scope, b: Scope): Int = a.compare(b)
  }

}
