package com.softwaremill.macwire.akkasupport.internals

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.softwaremill.macwire.Logger
import com.softwaremill.macwire.dependencyLookup.DependencyResolver

import scala.reflect.macros.blackbox

private[macwire] final class Crimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._
  lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe
  lazy val dependencyResolver = new DependencyResolver[c.type](c, log)

  lazy val targetConstructor: Symbol = log.withBlock(s"Looking for targetConstructor for $targetType"){
    val publicConstructors = targetType.members
      .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
      .filterNot(isPhantomConstructor)
    log.withBlock(s"There are ${publicConstructors.size} eligible constructors" ) { publicConstructors.foreach(c => log(showConstructor(c))) }
    val isInjectAnnotation = (a: Annotation) => a.toString == "javax.inject.Inject"
    val injectConstructors = publicConstructors.filter(_.annotations.exists(isInjectAnnotation))
    val injectConstructor = if(injectConstructors.size > 1) c.abort(c.enclosingPosition, s"Ambiguous constructors annotated with @javax.inject.Inject for type [$targetType]") else injectConstructors.headOption
    lazy val primaryConstructor = publicConstructors.find(_.asMethod.isPrimaryConstructor)
    val ctor: Symbol = injectConstructor orElse primaryConstructor getOrElse c.abort(c.enclosingPosition, s"Cannot find a public constructor for [$targetType]")
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

  /**
    * In some cases there is one extra (phantom) constructor.
    * This happens when extended trait has implicit param:
    *
    * {{{
    *   trait A { implicit val a = ??? };
    *   class X extends A
    *   import scala.reflect.runtime.universe._
    *   typeOf[X].members.filter(m => m.isMethod && m.asMethod.isConstructor && m.asMethod.isPrimaryConstructor).map(_.asMethod.fullName)
    *
    *  //res1: Iterable[String] = List(X.<init>, A.$init$)
    *  }}}
    *
    *  The {{{A.$init$}}} is the phantom constructor and we don't want it.
    *
    *  In other words, if we don't filter such constructor using this function
    *  'wireActor-12-noPublicConstructor.failure' will compile and throw exception during runtime but we want to fail it during compilation time.
    */
  private def isPhantomConstructor(constructor: Symbol): Boolean = constructor.asMethod.fullName.endsWith("$init$")
}
