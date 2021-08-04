package com.softwaremill.macwire
package catseffectsupport

import scala.reflect.macros.blackbox
import cats.effect._
import com.softwaremill.macwire.internals._

object MacwireCatsEffectMacros {
    private val log = new Logger()

    /**
      *  wire as usual, but when we've got an instance which may be created as resource we use it, but do not acquire the resource till it's possible to delay it (till we've got all required components)
      
      * 
      */
  def wireResourceRec_impl[T](c: blackbox.Context): c.Expr[Resource[IO, T]] = {
    import c.universe._
    implicit val wttp = weakTypeTag[Resource[IO, T]]

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName
      
      !name.startsWith("java.lang.") && !name.startsWith("scala.") 
    }

    def isResource(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName
      
      !name.startsWith("cats.Resource.")
    }

    val dependencyResolver = new DependencyResolver[c.type, Type, Tree](c, log)(tpe => 
      if(isResource(tpe)) c.Expr[T](q"wireResourceRec[$tpe]").tree
      else if (!isWireable(tpe)) c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")
      else c.Expr[T](q"wireResourceRec[cats.Resource[cats.effect.IO, $tpe]]").tree
    )

    val constructorCrimper = new ConstructorCrimper[c.type,  Resource[IO, T]](c, log)    
    val companionCrimper = new CompanionCrimper[c.type, Resource[IO, T]](c, log)

    lazy val targetType = companionCrimper.targetType.toString

    val code: Tree = (companionCrimper.applyTree(dependencyResolver) orElse constructorCrimper.constructorTree(dependencyResolver)) getOrElse
      c.abort(c.enclosingPosition, "Err")
    println(s"Generated code: ${showCode(code)}, ${showRaw(code)}")
    c.Expr(code)
  
  }

  def wireResource[T: c.WeakTypeTag](c: blackbox.Context)(dependencyResolver: DependencyResolver[c.type, c.universe.Type, c.universe.Tree]): c.Expr[T] = {
    import c.universe._

    val constructorCrimper = new ConstructorCrimper[c.type, T](c, log)
    val companionCrimper = new CompanionCrimper[c.type, T](c, log)

    lazy val targetType = companionCrimper.targetType.toString
    lazy val whatWasWrong: String = {
      if (constructorCrimper.constructor.isEmpty && companionCrimper.companionType.isEmpty)
        s"Cannot find a public constructor nor a companion object for [$targetType]"
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.isEmpty)
        s"Companion object for [$targetType] has no apply methods constructing target type."
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.size > 1)
        s"No public primary constructor found for $targetType and multiple matching apply methods in its companion object were found."
      else s"Target type not supported for wiring: $targetType. Please file a bug report with your use-case."
    }

    val code: Tree = (constructorCrimper.constructorTree(dependencyResolver) orElse companionCrimper.applyTree(dependencyResolver)) getOrElse
      c.abort(c.enclosingPosition, whatWasWrong)
    log(s"Generated code: ${showCode(code)}, ${showRaw(code)}")
    c.Expr(code)
  }


  private def buildDependenciesGraph[T: c.WeakTypeTag](c: blackbox.Context) = {

  }



}
