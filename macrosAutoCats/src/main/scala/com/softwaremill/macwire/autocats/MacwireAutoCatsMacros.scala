package com.softwaremill.macwire.autocats

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._
import com.softwaremill.macwire.autocats.internals._

object MacwireAutoCatsMacros {
  private val log = new Logger()

  def autowire_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._

    val targetType = implicitly[c.WeakTypeTag[T]]

    val graphContext = new CatsProvidersGraphContext[c.type](c, log)
    val graph = graphContext.buildGraph(dependencies.toList, targetType.tpe)
    val sortedProviders = graph.topologicalSort()

    log(s"Sorted providers [${sortedProviders.mkString(", ")}]")

    val code = sortedProviders
      .map {
        case fm: graphContext.FactoryMethod => fm.result
        case p                              => p
      }
      .collect { case p @ (_: graphContext.Effect | _: graphContext.Resource) => p }
      .foldRight(
        q"cats.effect.Resource.pure[cats.effect.IO, $targetType](${graph.root.ident})"
      ) { case (resource, acc) =>
        q"${resource.value}.flatMap((${resource.ident}: ${resource.resultType}) => $acc)"
      }

    log(s"CODE: [$code]")

    c.Expr[CatsResource[IO, T]](code)
  }

}
