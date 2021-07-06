package com.softwaremill.macwire.internals

import scala.quoted.*
import EligibleValuesFinder.Scope.*

private[macwire] class DependencyResolver[Q <: Quotes, T: Type](using val q: Q)(debug: Logger, resolutionFallback: q.reflect.TypeRepr => q.reflect.Term) {
  import q.reflect.*
  private val eligibleValuesFinder = new EligibleValuesFinder[q.type](debug)

  private lazy val eligibleValues = eligibleValuesFinder.find()

  /** Look for a single instance of type `t`.
    * If either no instance or multiple instances are found,
    * a compilation error is reported and `None` is returned.
    */
  def resolve(param: Symbol, t: TypeRepr): Term = {

    eligibleValues.findInFirstScope(t).toList match {
      case Nil =>  resolutionFallback(t) 
      case value :: Nil =>
        val forwardValues = eligibleValues.findInScope(t, LocalForward)
        if (forwardValues.nonEmpty) {
          report.warning(s"Found [$value] for parameter [${param.name}], " +
            s"but a forward reference [${forwardValues.mkString(", ")}] was also eligible")
        }
        Ref(value.symbol).changeOwner(Symbol.spliceOwner.owner.owner)
        // match {
        //   case Select(This(_), a) => Select(This(param.owner), value.symbol)
        //   case a => a
        // }
        
      case values => report.throwError(s"Found multiple values of type [${showTypeName(t)}]: [$values]")
    }
  }
  

  /** @return all the instances of type `t` that are accessible.
    */
  def resolveAll(t: TypeRepr): Iterable[Tree] = {
    eligibleValues.findInAllScope(t)
  }
}

object DependencyResolver {
  def throwErrorOnResolutionFailure[Q <: Quotes, T: Type](debug: Logger)(using q: Q) = new DependencyResolver[q.type, T](using q)(debug, tpe => q.reflect.report.throwError(s"Cannot find a value of type: [${showTypeName(tpe)}]"))
}