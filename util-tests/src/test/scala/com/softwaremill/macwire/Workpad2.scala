package com.softwaremill.macwire

import com.softwaremill.tagging._

class AA()

trait X

object WorkpadApp extends App {
  val a = new AA().taggedWith[X]
}

//class A()
//class B(a: A)
//class C(a: A)
//
//trait X {
//  def a: A
//}
//
//trait Y {
//  lazy val a = wire[A]
//  lazy val c = wire[C]
//}
//
//object Z extends Y with X {
//  val b = wire[B]
//}
