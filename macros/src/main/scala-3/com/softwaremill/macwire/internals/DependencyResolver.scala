package com.softwaremill.macwire.internals

import scala.quoted.*

private[macwire] class DependencyResolver[Q <: Quotes, T: Type](debug: Logger)(using val q: Q) {
  import q.reflect.*
  // private val eligibleValuesFinder = new EligibleValuesFinder[c.type](c, debug)

  // private lazy val eligibleValues = eligibleValuesFinder.find()

  /** Look for a single instance of type `t`.
    * If either no instance or multiple instances are found,
    * a compilation error is reported and `None` is returned.
    */
  def resolve(param: Symbol): Term = {
    val wiredDef = Symbol.spliceOwner.owner
    val wiredOwner = wiredDef.owner
    val paramType = Ref(param).tpe.widen

    wiredOwner.memberFields.filter(field =>
      if (wiredDef == field) {
        println(s"  Not checking: $field")
        false
      } else {
        println(s"  Checking: $field")
        val fieldType = Ref(field).tpe.widen
        fieldType <:< paramType
      }
    ) match {
      case Nil => report.throwError(s"Cannot find value for type: $paramType")
      case l@(first :: second :: _) => report.throwError(s"For type: $paramType, found multiple values: $l")
      case List(field) => Ref(field)
    }
  }
  
  
  // {

  //   eligibleValues.findInFirstScope(t).toList match {
  //     case Nil => c.abort(c.enclosingPosition, s"Cannot find a value of type: [$t]")
  //     case value :: Nil =>
  //       val forwardValues = eligibleValues.findInScope(t, LocalForward)
  //       if (forwardValues.nonEmpty) {
  //         c.warning(c.enclosingPosition, s"Found [$value] for parameter [${param.name}], " +
  //           s"but a forward reference [${forwardValues.mkString(", ")}] was also eligible")
  //       }
  //       value
  //     case values => c.abort(c.enclosingPosition, s"Found multiple values of type [$t]: [$values]")
  //   }
  // }

  /** @return all the instances of type `t` that are accessible.
    */
  // def resolveAll(t: Type): Iterable[Tree] = {
  //   eligibleValues.findInAllScope(t)
  // }
}
