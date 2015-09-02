package com.softwaremill.macwire.implicits

import com.softwaremill.macwire._

// For some reason this compiles when run as a compile-test, hence putting it in tests2.
object ImplicitAndUsageInObject {
  case class B()
  case class A(implicit val b: B)

  implicit val b = new B
  val a = wire[A]
}
