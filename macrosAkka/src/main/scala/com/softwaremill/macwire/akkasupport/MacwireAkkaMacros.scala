package com.softwaremill.macwire
package akkasupport

import akka.actor.{ActorRef, Props}
import com.softwaremill.macwire.internals.Logger

import scala.reflect.macros.blackbox

object MacwireAkkaMacros {
  private val log = new Logger()

  def                      wireProps_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Props] = new internals.Crimper[c.type, T](c, log).wireProps
  def             wireAnonymousActor_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireAnonymousActor
  def                      wireActor_Impl[T: c.WeakTypeTag](c: blackbox.Context)(name: c.Expr[String]): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireActor(name)

  def          wirePropsWithProducer_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Props] = new internals.Crimper[c.type, T](c, log).wirePropsWithProducer
  def wireAnonymousActorWithProducer_Impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireAnonymousActorWithProducer
  def          wireActorWithProducer_Impl[T: c.WeakTypeTag](c: blackbox.Context)(name: c.Expr[String]): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireActorWithProducer(name)

  def           wirePropsWithFactory_Impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree): c.Expr[Props] = new internals.Crimper[c.type, T](c, log).wirePropsWithFactory(factory)
  def  wireAnonymousActorWithFactory_Impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireAnonymousActorWithFactory(factory)
  def           wireActorWithFactory_Impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree)(name: c.Expr[String]): c.Expr[ActorRef] = new internals.Crimper[c.type, T](c, log).wireActorWithFactory(factory)(name)
}
