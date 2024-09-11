package com.softwaremill.macwire

import com.softwaremill.macwire.autowire

case class A1()
case class A2()
case class B1()
case class B2()
case class C(b1: B1, b2: B2)(using A1, A2):
  def a1 = summon[A1]

object Test extends App {
  given A1 = A1()
  given A2 = A2()
  val c = autowire[C]()

  println(c.a1 eq summon[A1])
}
