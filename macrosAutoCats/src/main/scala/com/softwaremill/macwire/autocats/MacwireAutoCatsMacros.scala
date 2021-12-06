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

    val graph = new CatsProvidersGraphContext[c.type](c, log)
    val sortedProviders = graph.buildGraph(dependencies.toList).topologicalOrder()

    log(s"Sorted providers [${sortedProviders.mkString(", ")}]")
    
    val code = sortedProviders.map {
      case fm: graph.FactoryMethod => fm.result
      case p => p
    }.collect {
      case e: graph.Effect => e
      case r: graph.Resource => r
    }.foldRight(
      q"cats.effect.Resource.pure[cats.effect.IO, $targetType](com.softwaremill.macwire.autowire[$targetType](..${sortedProviders.map(_.ident)}))"
    ) { case (resource, acc) =>
      q"${resource.value}.flatMap((${resource.ident}: ${resource.resultType}) => $acc)"
    }
    log(s"CODE: [$code]")
   
    c.Expr[CatsResource[IO, T]](code)
  }

}
