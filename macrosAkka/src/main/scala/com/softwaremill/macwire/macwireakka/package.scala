package com.softwaremill.macwire

import akka.actor.{Actor, ActorRef, Props}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

package object macwireakka {
  def wireProps[T <: Actor]: Props = macro MacwireAkkaMacros.wireProps_Impl[T]
  def wireAnonymousActor[T <: Actor]: ActorRef =  macro MacwireAkkaMacros.wireAnonymousActor_Impl[T]
}
