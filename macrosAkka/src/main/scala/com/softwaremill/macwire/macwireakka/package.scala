package com.softwaremill.macwire

import akka.actor.{Actor, Props}

import scala.language.experimental.macros

package object macwireakka {
  def wireProps[T <: Actor]: Props = macro MacwireAkkaMacros.wireProps_Impl[T]
}
