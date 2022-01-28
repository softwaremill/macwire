package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._

class CatsProvidersGraphContext[C <: blackbox.Context](val c: C, val log: Logger)
    extends CatsProviders[C]
    with GraphBuilderUtils[C] {

  case class Param(symbol: c.Symbol, tpe: c.Type)
  lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  class CatsProvidersGraph(providers: List[Provider], val root: Provider) {
    log(s"Created graph for providers [${mkStringProviders(providers)}]")

    private def verifyOrder(resultProviders: List[Provider]) = {
      log.withBlock(s"Verifying order of [${mkStringProviders(resultProviders)}]") {
        def go(provider: Provider): Set[Provider] =
          provider.dependencies.flatten.foldl(Set(provider))((result, maybeProvider) => go(maybeProvider) ++ result)

        resultProviders.lastOption.map(go).map { usedProviders =>
          val notUsedProviders = providers.diff(usedProviders.toSeq)

          if (notUsedProviders.nonEmpty)
            c.abort(
              c.enclosingPosition,
              s"Not used providers for the following types [${notUsedProviders.map(_.resultType).mkString(", ")}]"
            )
        }
      }
    }

    private def failOnMissingDependencies() = log.withBlock("Checking missing dependencies"){
      type ProviderPath = List[Provider]
      case class CheckContext(currentPath: ProviderPath, missingPaths: Set[ProviderPath]) {
        def withProvider(provider: Provider)(f: CheckContext => CheckContext) = {
          val currentCtx =  copy(currentPath = currentPath :+ provider)
          val processedCtx = f(currentCtx)
          copy(missingPaths = processedCtx.missingPaths)
        }

        def missingPath(leaf: Provider): CheckContext = copy(missingPaths = missingPaths.+(currentPath :+ leaf))
      }

      object CheckContext {
        lazy val empty = CheckContext(List.empty, Set.empty)
      }

      def go(ctx: CheckContext)(provider: Provider): CheckContext = log.withBlock(s"Checking provider [$provider] for type [${provider.resultType}]") {
        ctx.withProvider(provider) { currentCtx =>
          provider match {
            case p: NotResolvedProvider => currentCtx.missingPath(p)
            case _ => provider.dependencies.foldLeft(currentCtx) { case (paramsCtx, deps) => deps.foldLeft(paramsCtx) { case (paramCtx, param) => go(paramCtx)(param)}}
          }
        }
      }

        val resultCtx = go(CheckContext.empty)(root)

        if (resultCtx.missingPaths.nonEmpty) {
          val msg = resultCtx.missingPaths.map { path =>
            val pathStr = path.map(_.symbol).mkString(".")
            val missingProvider = path.last.asInstanceOf[NotResolvedProvider]//FIXME should be typed
            s"Cannot construct instance of [${missingProvider.resultType}] on path [$pathStr]"
          }.mkString("\n")

          c.abort(c.enclosingPosition, msg)
        }
    }

    def topologicalSort(): List[Provider] = log.withBlock("Stable topological sort") {
      def go(provider: Provider, usedProviders: Set[Provider]): List[Provider] =
        log.withBlock(s"Going deeper for type [${provider.resultType}]") {

          provider.dependencies.flatten.foldl(List.empty[Provider]) {
            case (r, p: NotResolvedProvider) => log.withBlock(s"Skipping not resolved provider for type [${p.resultType}]")(r)
            case (r, p) if (usedProviders ++ r.toSet).contains(p) =>
              log.withBlock(s"Already used provider for type [${p.resultType}]")(r)
            case (r, p) =>
              log.withResult { (r ::: go(p, (usedProviders ++ r.toSet) + p)) :+ p }(result =>
                s"Built list for the following types [${result.map(_.resultType).mkString(", ")}]"
              )
          }
        }

        val result = providers.foldLeft(List.empty[Provider]) {
          case (resultProviders, nextProvider) if resultProviders.contains(nextProvider) => resultProviders
          case (resultProviders, nextProvider) => resultProviders ::: go(nextProvider, resultProviders.toSet)
        } :+ root
        
        failOnMissingDependencies()
        verifyOrder(result)

        log(s"RESULT [${result.map(_.resultType)}]")
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
    //TODO we should find the root provider at the beginning of the `buildGraph` method. In case of None we should abort the whole resolution and the other case we should resolve it's dependencies
    val (resolvedCtx, rootProvider) = maybeResolveWithFactoryMethod(resolvedFMContext)(
      rootType
    ).getOrElse(
      resolveRootCreator(resolvedFMContext)(
        rootType,
        findCreator(rootType).getOrElse(
          c.abort(c.enclosingPosition, s"Cannot construct an instance of type: [$rootType]")
        )
      )
    )

    // orElse maybeResolveParamWithCreator(resolvedFMContext)(rootType))

    val inputProvidersTypes = inputProviders
      .map {
        case Left(value)  => value.resultType
        case Right(value) => value.resultType
      }

    val sortedProviders = sortProvidersWithInputOrder(inputProvidersTypes)(resolvedCtx)

    log(s"Input providers order [${sortedProviders.mkString(", ")}]")

    new CatsProvidersGraph(sortedProviders, rootProvider)
  }

  /** Make sure that we compose resources in the same order we received them
    */
  private def sortProvidersWithInputOrder(
      inputProvidersTypes: List[c.Type]
  )(resultContext: BuilderContext): List[Provider] = {
    val inputProviders = inputProvidersTypes.map(tpe =>
      resultContext.providers.find(_.resultType <:< tpe).getOrElse(c.abort(c.enclosingPosition, "Internal error"))
    )

    inputProviders ++ resultContext.providers.diff(inputProviders)
  }

  private def maybeResolveWithFactoryMethod(ctx: BuilderContext)(param: c.Type): Option[(BuilderContext, Provider)] =
    ctx.providers
      .find {
        case fm: FactoryMethod => fm.resultType <:< param
        case _                 => false
      }
      .map(r => (ctx, r))

  type CreatorType = (c.Symbol, List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)

  //TODO warn user if this step fails
  private def findCreator(param: c.Type): Option[(c.Symbol, List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)] =
    ConstructorCrimper.constructorFactory2(c, log)(param) orElse CompanionCrimper.applyFactory2(c, log)(param)

  private def resolveCreatorParams(
      ctx: BuilderContext
  )(param: c.Type, creator: CreatorType): (BuilderContext, FactoryMethod) = {
    val (methodSymbol, creatorParams, creatorF) = creator

    val paramsTypes = creatorParams.map(_.map(paramSym => Param(paramSym, paramType(c)(param, paramSym))))
    log.trace(s"Creator params [${paramsTypes.mkString(", ")}] for type [$param]")

    val (updatedCtx, resolvedConParams) = resolveParamsLists(ctx)(paramsTypes)

    val fm = new FactoryMethod(methodSymbol, param, param, resolvedConParams, creatorF)
    (updatedCtx.addProvider(fm), fm)
  }

  private def resolveRootCreator(
      ctx: BuilderContext
  )(tpe: c.Type, creator: CreatorType): (BuilderContext, FactoryMethod) =
    if (isWireable(c)(tpe)) resolveCreatorParams(ctx)(tpe, creator)
    else c.abort(c.enclosingPosition, s"[$tpe] is not wireable")

  private def resolveParamsList(ctx: BuilderContext)(params: List[Param]): (BuilderContext, List[Provider]) =
    params.foldLeft((ctx, List.empty[Provider])) { case ((currentCtx, resolvedParams), Param(paramSym, paramTpe)) =>
      currentCtx.resolve(paramTpe) match {
        case Some(Left(provider)) => (currentCtx, resolvedParams :+ provider)
        case Some(Right(fmt)) => {
          val (updatedCtx, fm) = resolveFactoryMethod(currentCtx)(fmt)

          (updatedCtx, resolvedParams :+ fm)
        }
        case None =>
          findCreator(paramTpe) match {
            case Some(creator) if isWireable(c)(paramTpe) =>
              val (updatedCtx, fm) = resolveCreatorParams(currentCtx)(paramTpe, creator)
              updatedCtx.logContext
              (updatedCtx, resolvedParams :+ fm)
            case _ => (ctx, resolvedParams :+ new NotResolvedProvider(paramTpe, paramSym))
          }
      }
    }

  private def resolveParamsLists(
      ctx: BuilderContext
  )(params: List[List[Param]]): (BuilderContext, List[List[Provider]]) =
    log.withBlock(s"Resolving params [${mkStringFrom2DimList(params)}]") {
      params.foldLeft((ctx, List.empty[List[Provider]])) { case ((currentCtx, resolvedParamsLists), paramsList) =>
        val (updatedCtx, resolvedParamsList) = resolveParamsList(currentCtx)(paramsList)
        (updatedCtx, resolvedParamsLists :+ resolvedParamsList)
      }
    }

  private def resolveFactoryMethod(ctx: BuilderContext)(fmt: FactoryMethodTree): (BuilderContext, FactoryMethod) = {
    import c.universe._

    val FactoryMethodTree(params, fun, resultType) = fmt

    log.withBlock(s"Resolving factory method [$fun]") {
      val paramsTypes = params.map { case s @ ValDef(_, name, tpt, rhs) =>
        Param(s.symbol, typeCheckUtil.typeCheckIfNeeded(tpt))
      }
      val (updatedCtx, deps) = resolveParamsLists(ctx)(List(paramsTypes))

      log(s"Resolved dependencies [${deps.map(_.mkString(", ")).mkString("\n")}]")

      val fm = new FactoryMethod(
        symbol = fun.symbol,
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

  /** DFS based algorithm that resolves all `FactoryMethodTree`
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
