package com.softwaremill.macwire.macwireakka
import akka.actor.Actor.Receive
import com.softwaremill.macwire._
import akka.actor.{Actor, ActorRef, ActorSystem}

object Demo extends App {

  trait A
  trait B
  trait C

  class SomeActor (a: A) extends Actor {
    override def receive: Receive = {
      case m => ???
    }
  }



  val system = ActorSystem("wireProps-5-injectAnnotation")
  val a = new A{}
//  val props: Props = wireProps[SomeActor]
  val someActor3: ActorRef = wireActor[SomeActor]("bob")

//  val someActor  = system.actorOf(props, "someActor")
//
//  val someActor2 = system.actorOf(Props(classOf[SomeActor], c), "someActor2")


  try {
//    someActor ! "Hey someActor"
//    someActor2 ! "Hey someActor"
    someActor3 ! "Hey someActor"
  } finally {
    Thread.sleep(100)
    system.terminate()
  }

}