package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.Debug
import com.softwaremill.macwire.Util._

import scala.reflect.macros.blackbox

private[macwire] class DependencyResolver[C <: blackbox.Context](
  val c: C,
  debug: Debug,
  wireWithImplicits: Boolean) {

  import c.universe._

  private lazy val implicitValuesFinder = new ImplicitValueOfTypeFinder[c.type](c, debug)
  private lazy val enclosingClassFinder = new ValuesOfTypeInEnclosingClassFinder[c.type](c, debug)
  private lazy val parentsMembersFinder = new ValuesOfTypeInParentsFinder[c.type](c, debug)

  private lazy val enclosingMethodsAndFuncsFinder = new ValuesOfTypeInEnclosingMethodsAndFunctionsFinder[c.type](c, debug)

  /** Look for a single instance of type `t`.
    * If either no instance or multiple instances are found,
    * a compilation error is reported and `None` is returned.
    */
  def resolve(param: Symbol, t: Type): Option[c.Tree] = {

    debug.withBlock(s"Trying to find value [${param.name}] of type: [$t]") {

      val results: List[Tree] = enclosingMethodsAndFuncsFinder.find(t) match {
        case Nil =>
          val implicitInferenceResults =
            if (wireWithImplicits || param.isImplicit) implicitValuesFinder.find(t)
            else None

          /* First, we perform plain, old, regular value lookup that will exclude found
           * implicit value if need */
          val regularLookupValues = firstNotEmpty[Tree](
            () => enclosingClassFinder.find(t, implicitInferenceResults),
            () => parentsMembersFinder.find(t, implicitInferenceResults)
          ).getOrElse(Nil)
          /* After regular lookup, we combine both results together.
           *
           * It's done as an separate step to prevent macro from resolving dependency too early
           * and thus shadowing non ambiguous resolution problems when:
           * 1) constructor parameter is marked as an implicit and
           * 2) there is no matching value in appropriate scope, but
           * 3) implicit value is present in some outer scope (parent, package/companion object,
           * explicit imports and so on).
           *
           * The implicit value tree may have been included if it was found as a regular value as well, hence
           * it has to be filtered out.
           */
          implicitInferenceResults.map(i => i :: regularLookupValues.filter(_ != i)).getOrElse(regularLookupValues)
        case values => values
      }

      results match {
        case Nil =>
          c.error(c.enclosingPosition, s"Cannot find a value of type: [$t]")
          None
        case value :: Nil =>
          debug(s"Found single value: [$value] of type [$t]")
          Some(value)
        case values =>
          c.error(c.enclosingPosition, s"Found multiple values of type [$t]: [$values]")
          None
      }
    }
  }

  /** @return all the instances of type `t` that are accessible.
    */
  def resolveAll(t: Type): List[c.Tree] = {
    debug.withBlock(s"Trying to find instances of type: [$t]") {
      enclosingMethodsAndFuncsFinder.find(t) ++
        enclosingClassFinder.find(t) ++
          parentsMembersFinder.find(t)
    }
  }
}
