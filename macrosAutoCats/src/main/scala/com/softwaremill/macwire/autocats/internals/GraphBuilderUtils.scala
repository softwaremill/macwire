package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._

trait  GraphBuilderUtils[C <: blackbox.Context] { this: CatsProvidersV2[C] => 
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
      providers: Map[c.Type, Provider],
      notResolvedFactoryMethods: Map[c.Type, FactoryMethodTree]
  ) {
    import c.universe._

    def resolvedFactoryMethod(provider: FactoryMethod): BuilderContext = copy(
      providers = providers.+((provider.resultType, provider.asInstanceOf[Provider])),
      notResolvedFactoryMethods = notResolvedFactoryMethods.removed(provider.resultType)
    )

    def resolve(tpe: Type): Option[Either[Provider, FactoryMethodTree]] = {
      val result = providers
        .get(tpe)//hehe it does not support inheritance :p
        .map(_.asLeft[FactoryMethodTree])
        .orElse(notResolvedFactoryMethods.get(tpe).map(_.asRight[Provider]))

      log(s"For type [$tpe] found [$result]")
      result
    }

    def next(): Option[FactoryMethodTree] = {
      val value = notResolvedFactoryMethods.headOption.map(_._2)
      log(s"Fetched next value [$value]")
      value
    }

    def addProvider(provider: Provider): BuilderContext =
      log.withBlock(s"For type [${provider.resultType}] add provider [${provider}]") {
        copy(
          providers = providers.+((provider.resultType, provider))
        )
      }
  }

}
