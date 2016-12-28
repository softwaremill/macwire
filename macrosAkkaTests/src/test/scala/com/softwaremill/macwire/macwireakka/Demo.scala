package com.softwaremill.macwire.macwireakka
import com.softwaremill.macwire._
import akka.actor.{Actor, ActorRef, ActorSystem}

object Demo extends App {

  trait A
  trait B
  trait C

  class SomeActor(a: A) extends Actor {

    def this(b: B) = {
      this(new A{})
      throw new UnsupportedOperationException()
    }

    @javax.inject.Inject
    def this(c: C) = this(new A{})

    override def receive: Receive = {
      case m => println(m)
    }
  }



  lazy val a: A = throw new UnsupportedOperationException()
  lazy val b: A = throw new UnsupportedOperationException()
  val c = new C {}

  val system = ActorSystem("wireProps-5-injectAnnotation")


//  val props: Props = wireProps[SomeActor]
  val someActor3: ActorRef = wireAnonymousActor[SomeActor]

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