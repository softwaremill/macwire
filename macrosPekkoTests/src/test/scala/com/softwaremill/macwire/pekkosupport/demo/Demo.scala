package com.softwaremill.macwire.pekkosupport.demo

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem}
import org.apache.pekko.routing.RoundRobinPool
import com.softwaremill.macwire._
import com.softwaremill.macwire.pekkosupport._
import com.softwaremill.tagging._

object Demo extends App {

  val system = ActorSystem("Demo")

  val score = new Score {}

  val violin: ActorRef @@ Violin = wireAnonymousActor[ViolinActor].taggedWith[Violin]
  val guitar: ActorRef @@ Guitar = wireAnonymousActor[GuitarActor].taggedWith[Guitar]

  val aliceSinger: ActorRef @@ Singer = wireActor[SingerActor]("alice").taggedWith[Singer]
  val bobSinger: ActorRef @@ Singer = wireActor[SingerActor]("bob").taggedWith[Singer]
  val bertaSinger = wireActor[SingerActor]("berta").taggedWith[Singer]
  val singers = wireSet[ActorRef @@ Singer]

  val `4 voices polyphone` = system.actorOf(wireProps[PolyphoneActor].withRouter(RoundRobinPool(4))).taggedWith[Polyphone]

  val orchestra = wireAnonymousActor[OrchestraActor]

  orchestra ! Play
  orchestra ! PlaySoloPolyphone
  orchestra ! PlaySoloPolyphone
  orchestra ! PlaySoloPolyphone

  Thread.sleep(100)
  system.terminate()
}


trait Score
case object Play
case object PlaySoloPolyphone

trait Polyphone
class PolyphoneActor extends Actor {
  override def receive: Receive = {
    case Play => println(s"Polyphone ${self.path.name} is playing: uouiiiiiiiiiii")
  }
}

trait Violin
class ViolinActor(score: Score) extends Actor {
  override def receive: Receive = {
    case Play => println("Violin is playing: uuuuuuu ...")
  }
}

trait Guitar
class GuitarActor(score: Score)  extends Actor {
  override def receive: Receive = {
    case Play => println("Guitar is playing: pa pa pppaaa  ...")
  }
}

trait Singer
class SingerActor(score: Score) extends Actor {
  override def receive: Receive = {
    case Play => println(s"${self.path.name} is singing: aaaaa uuuu eeee")
  }
}

trait Orchestra
class OrchestraActor(
  v: ActorRef @@ Violin,
  g: ActorRef @@ Guitar,
  polyphone: ActorRef @@ Polyphone,
  choir: Set[ActorRef @@ Singer]
) extends Actor {

  val supportiveChoirScore = new Score {}
  val supportiveChoir: ActorRef = wireActor[SingerActor]("supportive-choir")

  override def receive: Receive = {
    case Play =>
      v ! Play
      g ! Play
      g ! Play
      choir foreach (_ ! Play)
      supportiveChoir ! Play
      polyphone ! Play
    case PlaySoloPolyphone =>
      polyphone ! Play
    //...whatever
  }
}
