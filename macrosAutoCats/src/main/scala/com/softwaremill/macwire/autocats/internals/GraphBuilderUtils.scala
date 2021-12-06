package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._

trait  GraphBuilderUtils[C <: blackbox.Context] { this: CatsProviders[C] => 
    val c: C
    val log: Logger

    case class FactoryMethodTree(params: List[c.universe.ValDef], fun: c.Tree, resultType: c.Type)
  object FactoryMethodTree {
    import c.universe._

    def apply(tree: Tree): Option[FactoryMethodTree] = FactoryMethod.deconstruct { case (fun, params) =>
      FactoryMethodTree(params, fun, FactoryMethod.underlyingType(fun))
    }(tree)
  }

  case class BuilderContext(
      providers: List[Provider],
      notResolvedFactoryMethods: List[FactoryMethodTree]
  ) {
    import c.universe._

    def resolvedFactoryMethod(provider: FactoryMethod): BuilderContext = copy(
      providers = provider :: providers,
      notResolvedFactoryMethods = notResolvedFactoryMethods.filter(p => p.resultType != provider.resultType)
    )

    def resolve(tpe: Type): Option[Either[Provider, FactoryMethodTree]] = {
      val result = providers.find(_.resultType <:< tpe)
        .map(_.asLeft[FactoryMethodTree])
        .orElse(notResolvedFactoryMethods.find(_.resultType <:< tpe).map(_.asRight[Provider]))

      log(s"For type [$tpe] found [$result]")
      result
    }

    def next(): Option[FactoryMethodTree] = {
      val value = notResolvedFactoryMethods.headOption
      log(s"Fetched next value [$value]")
      value
    }

    def addProvider(provider: Provider): BuilderContext =
      log.withBlock(s"For type [${provider.resultType}] add provider [${provider}]") {
        copy(
          providers = provider :: providers
        )
      }
  }

}
