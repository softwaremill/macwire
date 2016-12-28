package com.softwaremill.macwire.macwireakka.internals

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.softwaremill.macwire.Logger
import com.softwaremill.macwire.dependencyLookup.DependencyResolver

import scala.reflect.macros.blackbox

class Crimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._
  lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe
  lazy val dependencyResolver = new DependencyResolver[c.type](c, log)

  lazy val targetConstructor: Option[Symbol] = {
    val publicConstructors = targetType.members.filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
    val isInjectAnnotation = (a: Annotation) => a.toString == "javax.inject.Inject"
    val injectConstructor = publicConstructors.find(_.annotations.exists(isInjectAnnotation)) //TODO: what if there are two constructors annotated with @Inject ?
    val primaryConstructor = publicConstructors.find(_.asMethod.isPrimaryConstructor)
    injectConstructor orElse primaryConstructor
  }

  lazy val targetConstructorParamLists: Option[List[List[Symbol]]] = targetConstructor.map(_.asMethod.paramLists.filterNot(_.headOption.exists(_.isImplicit)))

  def wireConstructorParams(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.foldLeft(List[List[Tree]]()) { case (acc, pList) =>
    val resolvedArgs: List[Tree] = pList.map(param => dependencyResolver.resolve(param, param.typeSignature).get)
    resolvedArgs :: acc
  }.reverse

  lazy val wiredConstructorParamLists: Option[List[Tree]] = targetConstructorParamLists
    .map(x => wireConstructorParams(x))
    .map(x => x.flatten)

  lazy val args: List[Tree] = wiredConstructorParamLists getOrElse c.abort(c.enclosingPosition, s"Cannot find a public constructor for $targetType")

  lazy val propsTree = q"akka.actor.Props(classOf[$targetType], ..$args)"

  lazy val wireProps = log.withBlock(s"Constructing Props. Trying to find arguments for constructor of: [$targetType] at ${c.enclosingPosition}") {
    log("Generated code: " + showRaw(propsTree)) //TODO: showCode ?
    c.Expr[Props](propsTree)
  }

  lazy val actorRefFactoryTree: Tree = log.withBlock("Looking for ActorRefFactory"){
    val actorRefType: Type = typeOf[ActorRefFactory]
    dependencyResolver.resolve(actorRefType.typeSymbol, actorRefType).get
  }

  lazy val wireAnonymousActor: c.Expr[ActorRef] = log.withBlock(s"Constructing ActorRef. Trying to find arguments for constructor of: [$targetType] at ${c.enclosingPosition}") {
    val tree = q"$actorRefFactoryTree.actorOf($propsTree)"
    log("Generated code: " + showRaw(tree)) //TODO: showCode ?
    c.Expr[ActorRef](tree)
  }
}
