package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._
import scala.collection.immutable

class CatsProvidersGraphContext[C <: blackbox.Context](val c: C, val log: Logger)
    extends CatsProvidersV2[C]
    with GraphBuilderUtils[C] {
  lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  class CatsProvidersGraph(providers: List[Provider], inputTypesOrder: List[c.Type]) {
    import c.universe._

    def topologicalOrder(): List[Provider] = {
      def go(remainingInputTypes: List[Type], resultProviders: List[Provider]): (List[Type], List[Provider]) =
        remainingInputTypes match {
          case Nil => (remainingInputTypes, resultProviders)
          case head :: _ =>
            log.withBlock(s"Build providers list from [$head]") {
              def goDeep(provider: Provider): List[Provider] =
                log.withBlock(s"Building deeper for type [${provider.resultType}] with [$provider]") {
                  provider.dependencies.flatten.foldl(List(provider)) {
                    case (r, None)                                   => r
                    case (r, Some(p)) if resultProviders.contains(p) => r
                    case (r, Some(p))                                => goDeep(p) ++ r
                  }
                }
              val ps = goDeep(
                providers
                  .find(_.resultType <:< head)
                  .getOrElse(c.abort(c.enclosingPosition, "Internal error. Missing provider"))
              )

              go(
                remainingInputTypes.diff(resultProviders.map(_.resultType) ++ ps.map(_.resultType)),
                resultProviders ++ ps
              )
            }
        }

      go(inputTypesOrder, List.empty)._2
    }

  }

  def buildGraphVertices(rawProviders: List[c.universe.Expr[Any]]): CatsProvidersGraph = {
    import c.universe._

    val (providers, fms): (Map[Type, Provider], Map[Type, FactoryMethodTree]) = rawProviders
      .partitionBifold { expr =>
        val tree = expr.tree
        val tpe = typeCheckUtil.typeCheckIfNeeded(tree)

        if (FactoryMethod.isFactoryMethod(tree))
          Right(
            FactoryMethod.underlyingResultType(tree) -> FactoryMethodTree(tree).get //FIXME
          )
        else if (Resource.isResource(tpe)) Left(Resource.underlyingType(tpe) -> new Resource(tree))
        else if (Effect.isEffect(tpe)) Left(Effect.underlyingType(tpe) -> new Effect(tree))
        else Left(tpe -> new Instance(tree))
      }
      .bimap(_.toMap, _.toMap)

    println(s"PROVIDERS [${providers.mkString(", ")}]")
    println(s"FMS [${fms.mkString(", ")}]")

    val resolvedCtx = resolveFactoryMethods(BuilderContext(providers.values.toList, fms.values.toList)) //FIXME
    log(s"Providers: [${resolvedCtx.providers.mkString(", ")}]")

    val inputProvidersOrder = rawProviders.map(expr => {
      val tree = expr.tree
      val tpe = typeCheckUtil.typeCheckIfNeeded(tree)
      if (FactoryMethod.isFactoryMethod(tree)) FactoryMethod.underlyingResultType(tree)
      else if (Resource.isResource(tpe)) Resource.underlyingType(tpe)
      else if (Effect.isEffect(tpe)) Effect.underlyingType(tpe)
      else tpe
    })

    log(s"Input providers order [${inputProvidersOrder.mkString(", ")}]")

    new CatsProvidersGraph(resolvedCtx.providers, inputProvidersOrder)
  }

  def maybeResolveParamWithConstructor(ctx: BuilderContext)(param: c.Type): Option[(BuilderContext, Constructor)] =
    log.withResult {
      ConstructorCrimper
        .constructorV3(c, log)(param)
        .map {
          case (constructorParams, creatorF) => {
            val constructorParamsTypes = constructorParams.map(_.map(s => ConstructorCrimper.paramType(c)(param, s)))
            log.trace(s"Constructor params [${constructorParamsTypes.mkString(", ")}]")

            val (updatedCtx, resolvedConParams) = resolveParamsLists(ctx)(constructorParamsTypes)
            (updatedCtx, Constructor(param, resolvedConParams, creatorF))
          }
        }
    } {
      case None                   => s"Failed to create constructor for type [$param]"
      case Some((_, constructor)) => s"For type [$param] created constructor [$constructor]"
    }

  def resolveParamsList(ctx: BuilderContext)(params: List[c.Type]): (BuilderContext, List[Option[Provider]]) =
    params.foldLeft((ctx, List.empty[Option[Provider]])) { case ((currentCtx, resolvedParams), param) =>
      currentCtx.resolve(param) match {
        case Some(Left(provider)) => (currentCtx, resolvedParams :+ Some(provider))
        case Some(Right(fmt)) => {
          val (updatedCtx, fm) = resolveFactoryMethod(currentCtx)(fmt)

          (updatedCtx, resolvedParams :+ Some(fm))
        }
        case None =>
          maybeResolveParamWithConstructor(currentCtx)(param) match {
            case Some((updatedCtx, constructor)) =>
              (updatedCtx.addProvider(constructor), resolvedParams :+ Some(constructor))
            case None => (ctx, resolvedParams :+ None)
          }
      }
    }

  def resolveParamsLists(
      ctx: BuilderContext
  )(params: List[List[c.Type]]): (BuilderContext, List[List[Option[Provider]]]) =
    log.withBlock(s"Resolving params [${mkStringFrom2DimList(params)}]") {
      params.foldLeft((ctx, List.empty[List[Option[Provider]]])) {
        case ((currentCtx, resolvedParamsLists), paramsList) =>
          val (updatedCtx, resolvedParamsList) = resolveParamsList(currentCtx)(paramsList)
          (updatedCtx, resolvedParamsLists :+ resolvedParamsList)
      }
    }

  def resolveFactoryMethod(ctx: BuilderContext)(fmt: FactoryMethodTree): (BuilderContext, FactoryMethod) = {
    import c.universe._

    val FactoryMethodTree(params, fun, resultType) = fmt

    log.withBlock(s"Resolving factory method [$fun]") {
      val paramsTypes = params.map { case ValDef(_, name, tpt, rhs) => typeCheckUtil.typeCheckIfNeeded(tpt) }
      val (updatedCtx, deps) = resolveParamsLists(ctx)(List(paramsTypes))

      log(s"Resolved dependencies [${deps.map(_.mkString(", ")).mkString("\n")}]")

      (
        updatedCtx,
        new FactoryMethod(
          fun = fun,
          resultType = resultType,
          dependencies = deps
        )
      )
    }
  }

  def resolveFactoryMethods(ctx: BuilderContext): BuilderContext = ctx.next() match {
    case None => ctx
    case Some(fmt) => {
      val (updatedCtx, fm) = resolveFactoryMethod(ctx)(fmt)

      resolveFactoryMethods(updatedCtx.resolvedFactoryMethod(fm))
    }
  }

  private def mkStringFrom2DimList[T](ll: List[List[T]]): String = ll.map(_.mkString(", ")).mkString("\n")
}
