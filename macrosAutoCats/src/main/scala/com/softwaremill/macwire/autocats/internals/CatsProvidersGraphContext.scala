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

  /** Creates a graph representation of a given providers list and all necessary intermediate providers that are
    * required to create an instance of the `rootType`.
    */
  def buildGraph(rawProviders: List[c.universe.Expr[Any]], rootType: c.Type): CatsProvidersGraph = {

    /** `FactoryMethodTree` does not extend the `Provider` trait, but it may be considered as a lazy representation of
      * `FactoryMethod` so the name `inputProviders` is appropriate
      */
    val inputProviders = rawProviders
      .map { expr =>
        val tree = expr.tree
        val tpe = typeCheckUtil.typeCheckIfNeeded(tree)

        FactoryMethodTree(tree)
          .map(_.asRight[Provider])
          .orElse(Resource.fromTree(tree, tpe).map(_.asLeft[FactoryMethodTree]))
          .orElse(Effect.fromTree(tree, tpe).map(_.asLeft[FactoryMethodTree]))
          .getOrElse(new Instance(tree).asLeft[FactoryMethodTree])
      }

    val (providers, fmts): (List[Provider], List[FactoryMethodTree]) =
      inputProviders.partitionBifold(identity)

    log(s"Providers: [${providers.mkString(", ")}]")
    log(s"Factory methods: [${fmts.mkString(", ")}]")

    val initContext = BuilderContext(providers, fmts)

    val resolvedFMContext = resolveFactoryMethods(initContext)

    /** We assume that we cannot use input provider directly, so we create a result object with available constructors.
      * It's a mimic of `wire`'s property
      */
    val (resolvedCtx, rootProvider) = maybeResolveParamWithCreator(resolvedFMContext)(rootType)
      .getOrElse(c.abort(c.enclosingPosition, s"Cannot construct an instance of type: [$rootType]"))

    val inputProvidersTypes = inputProviders
      .map {
        case Left(value)  => value.resultType
        case Right(value) => value.resultType
      }

    val sortedProviders = sortProvidersWithInputOrder(inputProvidersTypes)(resolvedCtx)

    log(s"Input providers order [${sortedProviders.mkString(", ")}]")

    new CatsProvidersGraph(sortedProviders, rootProvider)
  }

  /**
    * Make sure that we compose resources in the same order we received them
    */
  private def sortProvidersWithInputOrder(
      inputProvidersTypes: List[c.Type]
  )(resultContext: BuilderContext): List[Provider] = {
    val inputProviders = inputProvidersTypes.map(tpe =>
      resultContext.providers.find(_.resultType <:< tpe).getOrElse(c.abort(c.enclosingPosition, "Internal error"))
    )

    inputProviders ++ resultContext.providers.diff(inputProviders)
  }

  private def maybeResolveParamWithCreator(ctx: BuilderContext)(param: c.Type): Option[(BuilderContext, FactoryMethod)] =
    log.withBlock(s"Resolving creator for [$param]") {
      def maybeResolveParams(
          maybeFactory: Option[(List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)]
      ): Option[(BuilderContext, FactoryMethod)] = {
        maybeFactory.flatMap { case (creatorParams, creatorF) =>
          val paramsTypes = creatorParams.map(_.map(paramType(c)(param, _)))
          log.trace(s"Creator params [${paramsTypes.mkString(", ")}] for type [$param]")

          val (updatedCtx, resolvedConParams) = resolveParamsLists(ctx)(paramsTypes)

          if (resolvedConParams.exists(_.exists(_.isEmpty))) None
          else {
            val creator = new FactoryMethod(param, param, resolvedConParams, creatorF)
            Some((updatedCtx.addProvider(creator), creator))
          }
        }

      }

      if (!isWireable(c)(param)) None
      else
        maybeResolveParams(ConstructorCrimper.constructorFactory(c, log)(param))
          .orElse(maybeResolveParams(CompanionCrimper.applyFactory(c, log)(param)))

    }

  private def resolveParamsList(ctx: BuilderContext)(params: List[c.Type]): (BuilderContext, List[Option[Provider]]) =
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

  private def resolveParamsLists(
      ctx: BuilderContext
  )(params: List[List[c.Type]]): (BuilderContext, List[List[Option[Provider]]]) =
    log.withBlock(s"Resolving params [${mkStringFrom2DimList(params)}]") {
      params.foldLeft((ctx, List.empty[List[Option[Provider]]])) {
        case ((currentCtx, resolvedParamsLists), paramsList) =>
          val (updatedCtx, resolvedParamsList) = resolveParamsList(currentCtx)(paramsList)
          (updatedCtx, resolvedParamsLists :+ resolvedParamsList)
      }
    }

  private def resolveFactoryMethod(ctx: BuilderContext)(fmt: FactoryMethodTree): (BuilderContext, FactoryMethod) = {
    import c.universe._

    val FactoryMethodTree(params, fun, resultType) = fmt

    log.withBlock(s"Resolving factory method [$fun]") {
      val paramsTypes = params.map { case ValDef(_, name, tpt, rhs) => typeCheckUtil.typeCheckIfNeeded(tpt) }
      val (updatedCtx, deps) = resolveParamsLists(ctx)(List(paramsTypes))

      log(s"Resolved dependencies [${deps.map(_.mkString(", ")).mkString("\n")}]")

      val fm = new FactoryMethod(
        methodType = fun.symbol.asMethod.returnType,
        resultType = resultType,
        dependencies = deps,
        apply = _.foldLeft(fun)((acc: Tree, args: List[Tree]) => Apply(acc, args))
      )
      (
        updatedCtx.resolvedFactoryMethod(fm),
        fm
      )
    }
  }

  /**
    * DFS based algorithm that resolves all `FactoryMethodTree`
    */
  private def resolveFactoryMethods(ctx: BuilderContext): BuilderContext = ctx.next() match {
    case None => ctx
    case Some(fmt) => {
      val (updatedCtx, _) = resolveFactoryMethod(ctx)(fmt)

      resolveFactoryMethods(updatedCtx)
    }
  }

  private def mkStringFrom2DimList[T](ll: List[List[T]]): String = ll.map(_.mkString(", ")).mkString("\n")
  private def mkStringProviders(providers: List[Provider]) = providers.map(_.resultType).mkString(", ")
}
