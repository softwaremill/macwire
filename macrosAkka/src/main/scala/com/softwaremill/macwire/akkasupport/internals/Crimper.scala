package com.softwaremill.macwire.akkasupport.internals

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.softwaremill.macwire.internals.{ConstructorCrimper, Logger}

import scala.reflect.macros.blackbox

private[macwire] final class Crimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._

  lazy val cc = new ConstructorCrimper[c.type, T](c, new Logger)(implicitly[c.type#WeakTypeTag[T]])

  lazy val targetType: Type = cc.targetType

  lazy val args: List[Tree] = cc.constructorArgsWithImplicitLookups
    .getOrElse(c.abort(c.enclosingPosition, s"Cannot find a public constructor for [$targetType]"))
    .flatten

  lazy val propsTree = q"akka.actor.Props(classOf[$targetType], ..$args)"

  lazy val wireProps: c.Expr[Props] = log.withBlock(s"wireProps[$targetType]: at ${c.enclosingPosition}") {
    log("Generated code: " + showRaw(propsTree))
    c.Expr[Props](propsTree)
  }

  lazy val actorRefFactoryTree: Tree = log.withBlock("Looking for ActorRefFactory"){
    val actorRefType = typeOf[ActorRefFactory]
    val tree = cc.dependencyResolver.resolve(actorRefType.typeSymbol, actorRefType)
    log(s"Found ${showCode(tree)}")
    tree
  }

  lazy val wireAnonymousActor: c.Expr[ActorRef] = log.withBlock(s"Constructing ActorRef. Trying to find arguments for constructor of: [$targetType] at ${c.enclosingPosition}") {
    val tree = q"$actorRefFactoryTree.actorOf($propsTree)"
    log("Generated code: " + showRaw(tree))
    c.Expr[ActorRef](tree)
  }

  def wireActor(name: c.Expr[String]): c.Expr[ActorRef] = log.withBlock(s"wireActor[$targetType]: at ${c.enclosingPosition}") {
    val tree = q"$actorRefFactoryTree.actorOf($propsTree, ${name.tree})"
    log("Generated code: " + showRaw(tree))
    c.Expr[ActorRef](tree)
  }
}
