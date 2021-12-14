package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._

class CatsProvidersGraphContext[C <: blackbox.Context](val c: C, val log: Logger)
    extends CatsProviders[C]
    with GraphBuilderUtils[C] {
  lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  class CatsProvidersGraph(providers: List[Provider], inputTypesOrder: List[c.Type], val root: Provider) {
    import c.universe._

    //TODO simplify with root provider
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

  def buildGraph(rawProviders: List[c.universe.Expr[Any]], rootType: c.Type): CatsProvidersGraph = {
    val (providers, fms): (List[Provider], List[FactoryMethodTree]) = rawProviders
      .partitionBifold { expr =>
        val tree = expr.tree
        val tpe = typeCheckUtil.typeCheckIfNeeded(tree)

        FactoryMethodTree(tree)
          .map(_.asRight[Provider])
          .orElse(Resource.fromTree(tree, tpe).map(_.asLeft[FactoryMethodTree]))
          .orElse(Effect.fromTree(tree, tpe).map(_.asLeft[FactoryMethodTree]))
          .getOrElse(new Instance(tree).asLeft[FactoryMethodTree])
      }
      .bimap(_.toList, _.toList)

    log(s"Providers: [${providers.mkString(", ")}]")
    log(s"Factory methods: [${fms.mkString(", ")}]")

    val initContext = BuilderContext(providers, fms)
    val (resolvedCtx, rootProvider) = maybeResolveParamWithConstructor(resolveFactoryMethods(initContext))(rootType)
      .getOrElse(c.abort(c.enclosingPosition, s"Cannot find a value of type: [$rootType]"))

    val inputProvidersOrder = rawProviders.map(expr => {
      val tree = expr.tree
      val tpe = typeCheckUtil.typeCheckIfNeeded(tree)
      if (FactoryMethod.isFactoryMethod(tree)) FactoryMethod.underlyingResultType(tree)
      else if (Resource.isResource(tpe)) Resource.underlyingType(tpe)
      else if (Effect.isEffect(tpe)) Effect.underlyingType(tpe)
      else tpe
    })

    log(s"Input providers order [${inputProvidersOrder.mkString(", ")}]")

    new CatsProvidersGraph(resolvedCtx.providers, inputProvidersOrder, rootProvider)
  }

  def maybeResolveParamWithConstructor(ctx: BuilderContext)(param: c.Type): Option[(BuilderContext, Constructor)] =
    log.withBlock(s"Resolving constructor for [$param]") {
      def maybeResolveParams(
          maybeFactory: Option[(List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)]
      ): Option[(BuilderContext, Constructor)] = {
        maybeFactory.flatMap { case (constructorParams, creatorF) =>
          val paramsTypes = constructorParams.map(_.map(paramType(c)(param, _)))
          log.trace(s"Constructor params [${paramsTypes.mkString(", ")}] for type [$param]")

          val (updatedCtx, resolvedConParams) = resolveParamsLists(ctx)(paramsTypes)

          if (resolvedConParams.exists(_.exists(_.isEmpty))) None
          else {
            val constructor = Constructor(param, resolvedConParams, creatorF)
            Some((updatedCtx.addProvider(constructor), constructor))
          }
        }

      }

      if (!isWireable(c)(param)) None
      else
        maybeResolveParams(ConstructorCrimper.constructorFactory(c, log)(param))
          .orElse(maybeResolveParams(CompanionCrimper.applyFactory(c, log)(param)))

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
              (updatedCtx, resolvedParams :+ Some(constructor))
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

      val fm = new FactoryMethod(fun = fun, resultType = resultType, dependencies = deps)
      (
        updatedCtx.resolvedFactoryMethod(fm),
        fm
      )
    }
  }

  def resolveFactoryMethods(ctx: BuilderContext): BuilderContext = ctx.next() match {
    case None => ctx
    case Some(fmt) => {
      val (updatedCtx, _) = resolveFactoryMethod(ctx)(fmt)

      resolveFactoryMethods(updatedCtx)
    }
  }

  private def mkStringFrom2DimList[T](ll: List[List[T]]): String = ll.map(_.mkString(", ")).mkString("\n")
}
