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

    def resolve(t: Type, handler: (Type, List[Tree]) => Tree): Tree = {
      val trees: List[Tree] = eligibleValues.findInFirstScope(t).toList match {
        case Nil if isOption(t) => List(resolve(getOptionArg(t).get, optionHandler))
        case ts => ts
      }
      handler(t, trees)
    }

    def basicHandler(t: Type, trees: List[Tree]): Tree = trees match {
      case Nil => noValueError(t)
      case value :: Nil =>
        forwardValuesWarn(value)
        value
      case values => multipleValuesError(t, values)
    }

    def optionHandler(t: Type, trees: List[Tree]): Tree = trees match {
      case Nil => q"None"
      case value :: Nil if value.equalsStructure(q"None") => q"None"
      case value :: Nil =>
        forwardValuesWarn(value)
        q"Some($value)"
      case values => multipleValuesError(t, values)
    }

    def noValueError(t: Type): Nothing =
      c.abort(c.enclosingPosition, s"Cannot find a value of type: [$t]")

    def multipleValuesError(t: Type, values: List[Tree]): Nothing =
      c.abort(c.enclosingPosition, s"Found multiple values of type [$t]: [$values]")

    def forwardValuesWarn(value: Tree): Unit = {
      val forwardValues = eligibleValues.findInScope(t, LocalForward)
      if (forwardValues.nonEmpty) {
        c.warning(c.enclosingPosition, s"Found [$value] for parameter [${param.name}], " +
          s"but a forward reference [${forwardValues.mkString(", ")}] was also eligible")
      }
    }

    def isOption(t: Type): Boolean = getOptionArg(t).nonEmpty

    def getOptionArg(t: Type): Option[Type] = t.baseType(typeOf[Option[_]].typeSymbol) match {
      case TypeRef(_, _, arg :: Nil) => Some(arg)
      case NoType => None
    }

    resolve(t, basicHandler)
  }

  /** @return all the instances of type `t` that are accessible.
    */
  def resolveAll(t: Type): Iterable[Tree] = {
    eligibleValues.findInAllScope(t)
  }
}
