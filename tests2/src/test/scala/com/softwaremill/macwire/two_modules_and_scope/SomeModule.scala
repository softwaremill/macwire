package com.softwaremill.macwire.two_modules_and_scope

import com.softwaremill.macwire.Macwire
import com.softwaremill.macwire.scopes.Scope

trait SomeModule extends Macwire {
  val a: A = x(wire[A])
  val b: B = wire[B]

  def x: Scope
}


