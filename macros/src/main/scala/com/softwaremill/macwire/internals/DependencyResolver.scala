package com.softwaremill.macwire.internals

import com.softwaremill.macwire.internals.EligibleValuesFinder.Scope.LocalForward

import scala.reflect.macros.blackbox

private[macwire] class DependencyResolver[C <: blackbox.Context](val c: C, debug: Logger) {

  import c.universe._

  private val eligibleValuesFinder = new EligibleValuesFinder[c.type](c, debug)

  private lazy val eligibleValues = eligibleValuesFinder.find()

  /** Look for a single instance of type `t`.
    * If either no instance or multiple instances are found,
    * a compilation error is reported and `None` is returned.
    */
  def resolve(param: Symbol, t: Type): Tree = {

    eligibleValues.findInFirstScope(t).toList match {
      case Nil if isOption(t) =>
        getOptionArg(t).flatMap(u => eligibleValues.findInFirstScope(u).toList.headOption) match { //FIXME: handle multiple values (tests, tests...)
          case Some(argTree) => q"Some($argTree)"
          case None => q"None"
        }
      case Nil => c.abort(c.enclosingPosition, s"Cannot find a value of type: [$t]")
      case value :: Nil =>
        val forwardValues = eligibleValues.findInScope(t, LocalForward)
        if (forwardValues.nonEmpty) {
          c.warning(c.enclosingPosition, s"Found [$value] for parameter [${param.name}], " +
            s"but a forward reference [${forwardValues.mkString(", ")}] was also eligible")
        }
        value
      case values => c.abort(c.enclosingPosition, s"Found multiple values of type [$t]: [$values]")
    }
  }

  private def isOption(t: Type): Boolean = getOptionArg(t).nonEmpty

  private def getOptionArg(t: Type): Option[Type] = t.baseType(typeOf[Option[_]].typeSymbol) match {
    case TypeRef(_, _, arg :: Nil) => Some(arg)
    case NoType => None
  }

  

  /** @return all the instances of type `t` that are accessible.
    */
  def resolveAll(t: Type): Iterable[Tree] = {
    eligibleValues.findInAllScope(t)
  }
}
