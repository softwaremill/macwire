package com.softwaremill.macwire
package akkasupport

import akka.actor.{ActorRef, Props}

import scala.reflect.macros.blackbox

object MacwireAkkaMacros {
  private val log = new Logger()

  def          wireProps_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Props]    = new internals.Crimper[c.type, T](c, log).wireProps
  def wireAnonymousActor_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireAnonymousActor
  def          wireActor_Impl[T: c.WeakTypeTag](c: blackbox.Context)(name: c.Expr[String]): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireActor(name)
}
