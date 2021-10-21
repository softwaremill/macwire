package com.softwaremill.macwire.akkasupport.internals

import akka.actor.{ActorRef, ActorRefFactory, IndirectActorProducer, Props}
import com.softwaremill.macwire.internals.{ConstructorCrimper, Logger, DependencyResolver}

import scala.reflect.macros.blackbox

private[macwire] final class Crimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._
  lazy val dependencyResolver = DependencyResolver.throwErrorOnResolutionFailure[c.type, Type, Tree](c, log)
  lazy val cc = new ConstructorCrimper[c.type, T](c, new Logger)(implicitly[c.type#WeakTypeTag[T]])

  lazy val targetType: Type = cc.targetType

  lazy val args: List[Tree] = cc
    .constructorArgsWithImplicitLookups(dependencyResolver)
    .getOrElse(c.abort(c.enclosingPosition, s"Cannot find a public constructor for [$targetType]"))
    .flatten

  lazy val propsTree = q"akka.actor.Props(classOf[$targetType], ..$args)"

  lazy val wireProps: c.Expr[Props] = log.withBlock(s"wireProps[$targetType]: at ${c.enclosingPosition}") {
    log("Generated code: " + showRaw(propsTree))
    c.Expr[Props](propsTree)
  }

  lazy val actorRefFactoryTree: Tree = log.withBlock("Looking for ActorRefFactory") {
    val actorRefType = typeOf[ActorRefFactory]
    val tree = dependencyResolver.resolve(actorRefType.typeSymbol, actorRefType)
    log(s"Found ${showCode(tree)}")
    tree
  }

  lazy val wireAnonymousActor: c.Expr[ActorRef] = log.withBlock(
    s"Constructing ActorRef. Trying to find arguments for constructor of: [$targetType] at ${c.enclosingPosition}"
  ) {
    val tree = q"$actorRefFactoryTree.actorOf($propsTree)"
    log("Generated code: " + showRaw(tree))
    c.Expr[ActorRef](tree)
  }

  def wireActor(name: c.Expr[String]): c.Expr[ActorRef] =
    log.withBlock(s"wireActor[$targetType]: at ${c.enclosingPosition}") {
      val tree = q"$actorRefFactoryTree.actorOf($propsTree, ${name.tree})"
      log("Generated code: " + showRaw(tree))
      c.Expr[ActorRef](tree)
    }

  lazy val wirePropsWithProducer: c.Expr[Props] = weakTypeOf[T] match {
    case t if t <:< typeOf[IndirectActorProducer] => wireProps
    case _ => c.abort(c.enclosingPosition, s"wirePropsWith does not support the type: [$targetType]")
  }

  lazy val wireAnonymousActorWithProducer: c.Expr[ActorRef] = weakTypeOf[T] match {
    case t if t <:< typeOf[IndirectActorProducer] => wireAnonymousActor
    case _ => c.abort(c.enclosingPosition, s"wireAnonymousActorWith does not support the type: [$targetType]")
  }

  def wireActorWithProducer(name: c.Expr[String]): c.Expr[ActorRef] = weakTypeOf[T] match {
    case t if t <:< typeOf[IndirectActorProducer] => wireActor(name)
    case _ => c.abort(c.enclosingPosition, s"wireActorWith does not support the type: [$targetType]")
  }

  def wirePropsWithFactory(factory: c.Tree): c.Expr[Props] =
    log.withBlock(s"wireProps[$targetType]: at ${c.enclosingPosition}") {
      import com.softwaremill.macwire.MacwireMacros._

      val funTree = wireWith_impl(c)(factory)
      val propsTree = q"akka.actor.Props($funTree)"
      log("Generated code: " + showRaw(propsTree))
      c.Expr[Props](propsTree)
    }

  def wireAnonymousActorWithFactory(factory: c.Tree): c.Expr[ActorRef] = log.withBlock(
    s"Constructing ActorRef. Trying to find arguments for constructor of: [$targetType] at ${c.enclosingPosition}"
  ) {
    import com.softwaremill.macwire.MacwireMacros._

    val funTree = wireWith_impl(c)(factory)
    val propsTree = q"akka.actor.Props($funTree)"
    val tree = q"$actorRefFactoryTree.actorOf($propsTree)"
    log("Generated code: " + showRaw(tree))
    c.Expr[ActorRef](tree)
  }

  def wireActorWithFactory(factory: c.Tree)(name: c.Expr[String]): c.Expr[ActorRef] =
    log.withBlock(s"wireActorWithProducer[$targetType]: at ${c.enclosingPosition}") {
      import com.softwaremill.macwire.MacwireMacros._

      val funTree = wireWith_impl(c)(factory)
      val propsTree = q"akka.actor.Props($funTree)"
      val tree = q"$actorRefFactoryTree.actorOf($propsTree, ${name.tree})"
      log("Generated code: " + showRaw(tree))
      c.Expr[ActorRef](tree)
    }
}
