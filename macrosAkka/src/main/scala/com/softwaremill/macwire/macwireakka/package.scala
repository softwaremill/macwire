package com.softwaremill.macwire

import akka.actor.{Actor, ActorRef, Props}
import scala.language.experimental.macros

package object macwireakka {
  def wireProps[T <: Actor]: Props = macro MacwireAkkaMacros.wireProps_Impl[T]
  def wireAnonymousActor[T <: Actor]: ActorRef = macro MacwireAkkaMacros.wireAnonymousActor_Impl[T]
  def wireActor[T <: Actor](name: String): ActorRef = macro MacwireAkkaMacros.wireActor_Impl[T]
}
