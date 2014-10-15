package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.Debug

import scala.reflect.macros.blackbox.Context

private[dependencyLookup] class ImplicitValueOfTypeFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  def find(t: Type): Option[c.Tree] = {
    debug.withBlock("Looking for implicit value") {
      c.inferImplicitValue(t, true, false, c.enclosingPosition) match {
        case EmptyTree =>
          debug("There is no implicit values in scope")
          None
        case tree => {
          debug("Found matching implicit value")
          Some(tree)
        }
      }
    }
  }
}
