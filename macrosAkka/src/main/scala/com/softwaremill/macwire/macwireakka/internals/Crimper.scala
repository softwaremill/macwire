package com.softwaremill.macwire.macwireakka.internals

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.softwaremill.macwire.Logger
import com.softwaremill.macwire.dependencyLookup.DependencyResolver

import scala.reflect.macros.blackbox

private[macwire] final class Crimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._
  lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe
  lazy val dependencyResolver = new DependencyResolver[c.type](c, log)

  lazy val targetConstructor: Symbol = log.withBlock(s"Looking for targetConstructor for $targetType"){
    val publicConstructors = targetType.members.filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
    log.withBlock(s"There are ${publicConstructors.size} eligible constructors" ) { publicConstructors.foreach(c => log(showConstructor(c))) }
    val isInjectAnnotation = (a: Annotation) => a.toString == "javax.inject.Inject"
    val injectConstructor = publicConstructors.find(_.annotations.exists(isInjectAnnotation)) //TODO: what if there are two constructors annotated with @Inject ?
    lazy val primaryConstructor = publicConstructors.find(_.asMethod.isPrimaryConstructor)
    val ctor: Symbol = injectConstructor orElse primaryConstructor getOrElse c.abort(c.enclosingPosition, s"Cannot find a public constructor for $targetType")
    log(s"Found ${showConstructor(ctor)}")
    ctor
  }

  def showConstructor(c: Symbol): String = c.asMethod.typeSignature.toString

  def wireConstructorParams(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.foldLeft(List[List[Tree]]()) { case (acc, pList) =>
    val resolvedArgs: List[Tree] = pList.map(param => dependencyResolver.resolve(param, param.typeSignature).get)
    resolvedArgs :: acc
  }.reverse

  lazy val targetConstructorParamLists: List[List[Symbol]] = targetConstructor.asMethod.paramLists.filterNot(_.headOption.exists(_.isImplicit))

  lazy val args: List[Tree] = log.withBlock("Looking for constructor arguments") {
    wireConstructorParams(targetConstructorParamLists).flatten
  }

  lazy val propsTree = q"akka.actor.Props(classOf[$targetType], ..$args)"

  lazy val wireProps: c.Expr[Props] = log.withBlock(s"wireProps[$targetType]: at ${c.enclosingPosition}") {
    log("Generated code: " + showRaw(propsTree))
    c.Expr[Props](propsTree)
  }

  lazy val actorRefFactoryTree: Tree = log.withBlock("Looking for ActorRefFactory"){
    val actorRefType = typeOf[ActorRefFactory]
    val tree = dependencyResolver.resolve(actorRefType.typeSymbol, actorRefType).get
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
