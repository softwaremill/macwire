package com.softwaremill.macwire.two_modules_and_scope

import com.softwaremill.macwire._
import com.softwaremill.macwire.scopes.Scope

trait SomeModule {
  val a: A = x(wire[A])
  val b: B = wire[B]

  def x: Scope
}


