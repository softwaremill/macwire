package com.softwaremill.macwire.internals

import com.softwaremill.macwire.internals.EligibleValuesFinder.Scope.LocalForward

import scala.reflect.macros.blackbox

class DependencyResolver[C <: blackbox.Context, TypeC <: C#Type, TreeC <: C#Tree](val c: C, debug: Logger)(
    resolutionFallback: TypeC => TreeC
) {

  import c.universe._

  private val eligibleValuesFinder = new EligibleValuesFinder[c.type](c, debug)

  private lazy val eligibleValues = eligibleValuesFinder.find()

  /** Look for a single instance of type `t`. If either no instance or multiple instances are found, a compilation error
    * is reported and `None` is returned.
    */
  def resolve(param: Symbol, t: Type): Tree = {
    eligibleValues.findInFirstScope(t).toList match {
      case Nil => resolutionFallback(t.asInstanceOf[TypeC]).asInstanceOf[Tree]
      case value :: Nil =>
        val forwardValues = eligibleValues.findInScope(t, LocalForward)
        if (forwardValues.nonEmpty) {
          c.warning(
            c.enclosingPosition,
            s"Found [$value] for parameter [${param.name}], " +
              s"but a forward reference [${forwardValues.mkString(", ")}] was also eligible"
          )
        }
        value
      case values => c.abort(c.enclosingPosition, s"Found multiple values of type [$t]: [$values]")
    }
  }

  /** @return
    *   all the instances of type `t` that are accessible.
    */
  def resolveAll(t: Type): Iterable[Tree] = {
    eligibleValues.findInAllScope(t)
  }
}

object DependencyResolver {
  def throwErrorOnResolutionFailure[C <: blackbox.Context, TypeC <: C#Type, TreeC <: C#Tree](c: C, debug: Logger) =
    new DependencyResolver[C, TypeC, TreeC](c, debug)(t =>
      c.abort(c.enclosingPosition, s"Cannot find a value of type: [$t]")
    )
}
