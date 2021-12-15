package com.softwaremill.macwire.autocats

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._
import cats.implicits._
import com.softwaremill.macwire.MacwireMacros
import com.softwaremill.macwire.autocats.internals._

object MacwireCatsEffectMacros {
  private val log = new Logger()

  def autowire_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._

    val targetType = implicitly[c.WeakTypeTag[T]]

    val graphContext = new CatsProvidersGraphContext[c.type](c, log)
    val graph = graphContext.buildGraph(dependencies.toList, targetType.tpe)
    val sortedProviders = graph.topologicalOrder()

    log(s"Sorted providers [${sortedProviders.mkString(", ")}]")
    
    val code = sortedProviders.map {
      case fm: graphContext.FactoryMethod => fm.result
      case p => p
    }.collect {
      case e: graphContext.Effect => e
      case r: graphContext.Resource => r
      case con: graphContext.Constructor => con
    }.foldRight(
      q"cats.effect.Resource.pure[cats.effect.IO, $targetType](${graph.root.ident})"
    ) { case (resource, acc) =>
      q"${resource.value}.flatMap((${resource.ident}: ${resource.resultType}) => $acc)"
    }
    log(s"CODE: [$code]")
   
    c.Expr[CatsResource[IO, T]](code)
  }

}
