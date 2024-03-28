package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._
import scala.collection.immutable

trait GraphBuilderUtils[C <: blackbox.Context] { this: CatsProviders[C] =>
  val c: C
  val log: Logger

  /** Lazy representation of FactoryMethod It's required because we may need to use a factory method to construct an
    * intermediate instance in graph building process
    */
  case class FactoryMethodTree(params: List[c.universe.ValDef], fun: c.Tree, resultType: c.Type)
  object FactoryMethodTree {
    import c.universe._

    def apply(tree: Tree): Option[FactoryMethodTree] = FactoryMethod.deconstruct { case (fun, params) =>
      FactoryMethodTree(params, fun, FactoryMethod.underlyingType(fun))
    }(tree)
  }

  case class BuilderContext private (
      providers: List[Provider],
      notResolvedFactoryMethods: List[FactoryMethodTree]
  ) {
    import c.universe._

    def logContext = log(
      s"Available instances in context: [${providers.map(_.resultType)}] & [${notResolvedFactoryMethods.map(_.resultType)}]"
    )
    def resolvedFactoryMethod(provider: FactoryMethod): BuilderContext = copy(
      providers = provider :: providers,
      notResolvedFactoryMethods = notResolvedFactoryMethods.filter(p => p.resultType != provider.resultType)
    )

    def resolve(tpe: Type): Option[Either[Provider, FactoryMethodTree]] = {
      logContext
      val resolvedProviders = providers
        .filter(_.resultType <:< tpe)
        .map(_.asLeft[FactoryMethodTree])

      val resolvedFMT = notResolvedFactoryMethods
        .filter(_.resultType <:< tpe)
        .map(_.asRight[Provider])

      val result = (resolvedProviders ++ resolvedFMT) match {
        case Nil          => None
        case List(result) => Some(result)
        case ambiguousProviders => {
          val duplicates = ambiguousProviders.map {
            case Left(value)  => value.resultType
            case Right(value) => value.resultType
          }.toSet

          c.abort(c.enclosingPosition, s"Ambiguous instances of types [${duplicates.mkString(", ")}]")
        }
      }

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
