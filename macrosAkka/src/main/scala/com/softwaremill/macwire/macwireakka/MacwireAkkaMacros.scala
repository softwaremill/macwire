package com.softwaremill.macwire
package macwireakka

import akka.actor.Props

import scala.reflect.macros.blackbox

object MacwireAkkaMacros {
  private val log = new Logger()

  def wireProps_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Props] = new internals.Crimper[c.type, T](c, log).wireProps
}
