package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._

class CatsProvidersGraphContext[C <: blackbox.Context](val c: C, val log: Logger)
    extends CatsProviders[C]
    with GraphBuilderUtils[C] {
  lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  class CatsProvidersGraph(providers: List[Provider], val root: Provider) {
    log(s"Created graph for providers [${mkStringProviders(providers)}]")
    
    private def verifyOrder(resultProviders: List[Provider]) =
      log.withBlock(s"Verifying order of [${mkStringProviders(resultProviders)}]") {
        def go(provider: Provider): Set[Provider] =
          provider.dependencies.flatten.foldl(Set(provider))((result, maybeProvider) =>
            maybeProvider.map(go).getOrElse(Set.empty) ++ result
          )

        resultProviders.lastOption.map(go).map { usedProviders =>
          val notUsedProviders = providers.diff(usedProviders.toSeq)

          if (notUsedProviders.nonEmpty)
            c.abort(
              c.enclosingPosition,
              s"Not used providers for the following types [${notUsedProviders.map(_.resultType).mkString(", ")}]"
            )
        }
      }

    def topologicalSort(): List[Provider] = log.withBlock("Stable topological sort") {
      def go(provider: Provider, usedProviders: Set[Provider]): List[Provider] =
        log.withBlock(s"Going deeper for type [${provider.resultType}]") {

          provider.dependencies.flatten.foldl(List.empty[Provider]) {
            case (_, None) => c.abort(c.enclosingPosition, "Missing dependency.")
            case (r, Some(p)) if (usedProviders ++ r.toSet).contains(p) =>
              log.withBlock(s"Already used provider for type [${p.resultType}]")(r)
            case (r, Some(p)) =>
              log.withResult { (r ::: go(p, (usedProviders ++ r.toSet) + p)) :+ p }(result =>
                s"Built list for the following types [${result.map(_.resultType).mkString(", ")}]"
              )
          }
        }

        val result = providers.foldLeft(List.empty[Provider]) {
          case (resultProviders, nextProvider) if resultProviders.contains(nextProvider) => resultProviders
          case (resultProviders, nextProvider) => resultProviders ::: go(nextProvider, resultProviders.toSet)
        } :+ root

        verifyOrder(result)

        result
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
    val (resolvedCtx, rootProvider) = maybeResolveParamWithCreator(resolveFactoryMethods(initContext))(rootType)
      .getOrElse(c.abort(c.enclosingPosition, s"Cannot find a value of type: [$rootType]"))

    val inputProvidersOrder = rawProviders
      .map(expr => {
        val tree = expr.tree
        val tpe = typeCheckUtil.typeCheckIfNeeded(tree)
        if (FactoryMethod.isFactoryMethod(tree)) FactoryMethod.underlyingResultType(tree)
        else if (Resource.isResource(tpe)) Resource.underlyingType(tpe)
        else if (Effect.isEffect(tpe)) Effect.underlyingType(tpe)
        else tpe
      })
      .map(tpe =>
        resolvedCtx.providers.find(_.resultType <:< tpe).getOrElse(c.abort(c.enclosingPosition, "Internal error"))
      )

    log(s"Input providers order [${inputProvidersOrder.mkString(", ")}]")

    new CatsProvidersGraph(inputProvidersOrder ++ resolvedCtx.providers.diff(inputProvidersOrder), rootProvider)
  }

  def maybeResolveParamWithCreator(ctx: BuilderContext)(param: c.Type): Option[(BuilderContext, Creator)] =
    log.withBlock(s"Resolving creator for [$param]") {
      def maybeResolveParams(
          maybeFactory: Option[(List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)]
      ): Option[(BuilderContext, Creator)] = {
        maybeFactory.flatMap { case (creatorParams, creatorF) =>
          val paramsTypes = creatorParams.map(_.map(paramType(c)(param, _)))
          log.trace(s"Creator params [${paramsTypes.mkString(", ")}] for type [$param]")

          val (updatedCtx, resolvedConParams) = resolveParamsLists(ctx)(paramsTypes)

          if (resolvedConParams.exists(_.exists(_.isEmpty))) None
          else {
            val creator = Creator(param, resolvedConParams, creatorF)
            Some((updatedCtx.addProvider(creator), creator))
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
          maybeResolveParamWithCreator(currentCtx)(param) match {
            case Some((updatedCtx, creator)) =>
              (updatedCtx, resolvedParams :+ Some(creator))
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
  private def mkStringProviders(providers: List[Provider]) = providers.map(_.resultType).mkString(", ")
}
