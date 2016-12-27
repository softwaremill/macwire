package com.softwaremill.macwire.macwireakka

import akka.actor.{Actor, ActorSystem, Props}

object Demo extends App {

  trait A
  trait B
  trait C
  trait D

  object D {
    implicit val d = new D {}
  }

  class SomeActor(a: A)(b: B, c: C)(/* implicit doesn't work in actors  implicit */ d: D)  extends Actor {
    override def receive: Receive = {
      case m => //println(m)
    }
  }

  val system = ActorSystem("Test")

  val a = new A {}
  val b = new B {}
  val c = new C {}
  val d = implicitly[D]


  val bProps: Props = wireProps[SomeActor]
  val someActor = system.actorOf(bProps, "someActor")
  someActor ! "Hey someActor"


  val someOtherActor = system.actorOf(Props(classOf[SomeActor], a, b, c, d), "someOtherActor")
  someOtherActor ! "Hey someOtherActor"

  Thread.sleep(100)
  system.terminate()
}