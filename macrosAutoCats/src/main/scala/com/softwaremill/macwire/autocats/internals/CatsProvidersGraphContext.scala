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
          provider.dependencies.flatten.foldl(Set(provider)) { case (result, (_, dep)) => go(dep) ++ result }

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
      type ProviderPath = List[(c.Symbol, Provider)]
      case class CheckContext(currentPath: ProviderPath, missingPaths: Set[ProviderPath]) {
        def withProvider(sym: c.Symbol, provider: Provider)(f: CheckContext => CheckContext) = {
          val currentCtx =  copy(currentPath = currentPath.:+((sym, provider)))
          val processedCtx = f(currentCtx)
          copy(missingPaths = processedCtx.missingPaths)
        }

        def missingPath(): CheckContext = copy(missingPaths = missingPaths.+(currentPath))
      }

      object CheckContext {
        lazy val empty = CheckContext(List.empty, Set.empty)
      }

      def go(ctx: CheckContext)(sym: c.Symbol, provider: Provider): CheckContext = log.withBlock(s"Checking provider [$provider] for type [${provider.resultType}]") {
        ctx.withProvider(sym, provider) { currentCtx =>
          provider match {
            case _: NotResolvedProvider => currentCtx.missingPath()
            case _ => provider.dependencies.foldLeft(currentCtx) { case (paramsCtx, deps) => deps.foldLeft(paramsCtx) { case (paramCtx, (sym, param)) => go(paramCtx)(sym, param)}}
          }
        }
      }

        val resultCtx = go(CheckContext.empty)(c.universe.NoSymbol, root)

        if (resultCtx.missingPaths.nonEmpty) {
          def buildPathMsg(path: ProviderPath) = if (path.size <= 1) path.map(_._1).mkString
          else {
            val head = path.head._2
            val mid = path.drop(1).dropRight(1)
            val last = path.last._2

            val midStr = mid.map { case (sym, provider) => s".${sym.name} -> [${provider.symbol}]"}.mkString("", "", "")

            s"Missing dependency of type [${last.resultType}]. Path [${head.symbol}]$midStr.${last.symbol.name}"
          }

          val msg = resultCtx.missingPaths.map(buildPathMsg).mkString(s"Failed to create an instance of [${root.resultType}].\n", "\n", "\n")

          c.error(c.enclosingPosition, msg)
        }
    }

    def topologicalSort(): List[Provider] = log.withBlock("Stable topological sort") {
      def go(provider: Provider, usedProviders: Set[Provider]): List[Provider] =
        log.withBlock(s"Going deeper for type [${provider.resultType}]") {

          provider.dependencies.flatten.foldl(List.empty[Provider]) {
            case (r, (_, p: NotResolvedProvider)) => log.withBlock(s"Skipping not resolved provider for type [${p.resultType}]")(r)
            case (r, (_, p)) if (usedProviders ++ r.toSet).contains(p) =>
              log.withBlock(s"Already used provider for type [${p.resultType}]")(r)
            case (r, (_, p)) =>
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
    
    val (resolvedCtx, rootProvider) = maybeResolveWithFactoryMethod(resolvedFMContext)(
      rootType
    ).getOrElse(
       findResolvableCreator(resolvedFMContext)(rootType).getOrElse(
          c.abort(c.enclosingPosition, s"Cannot construct an instance of type: [$rootType]")
      )
    )

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

  private def findResolvableCreator(ctx: BuilderContext)(param: c.Type): Option[(BuilderContext, Provider)] = {
    val maybeConstructor = ConstructorCrimper.constructorFactory(c, log)(param).map(resolveCreatorParams(ctx)(param, _))
    val maybeApply = CompanionCrimper.applyFactory(c, log)(param).map(resolveCreatorParams(ctx)(param, _))

    def allDependenciesResolved(result: (BuilderContext, FactoryMethod)): Boolean = result._2.dependencies.flatten.collectFirst{case t@(_, _: NotResolvedProvider) => t}.isEmpty

    (maybeConstructor, maybeApply) match {
      //Found at least one resolvable creator
      case (Some(result), _) if allDependenciesResolved(result) => Some(result)
      case (_, Some(result)) if allDependenciesResolved(result) => Some(result)

      //Found at least one non-resolvable creator
      case (Some(result), _) => Some(result)
      case (None, Some(result)) => Some(result)

      //Not found any creator
      case (None, None) => None
    }
  }

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

  private def resolveParamsList(ctx: BuilderContext)(params: List[Param]): (BuilderContext, List[(c.Symbol, Provider)]) =
    params.foldLeft((ctx, List.empty[(c.Symbol, Provider)])) { case ((currentCtx, resolvedParams), Param(paramSym, paramTpe)) =>
      currentCtx.resolve(paramTpe) match {
        case Some(Left(provider)) => (currentCtx, resolvedParams.:+((paramSym, provider)))
        case Some(Right(fmt)) => {
          val (updatedCtx, fm) = resolveFactoryMethod(currentCtx)(fmt)

          (updatedCtx, resolvedParams.:+((paramSym, fm)))
        }
        case None if isWireable(c)(paramTpe) =>
          findResolvableCreator(currentCtx)(paramTpe).map { case (updatedCtx, fm) => 
            updatedCtx.logContext
            (updatedCtx, resolvedParams.:+((paramSym, fm)))
           }.getOrElse(((ctx, resolvedParams.:+((paramSym, new NotResolvedProvider(paramTpe, paramSym))))))
        case _ => (ctx, resolvedParams.:+((paramSym, new NotResolvedProvider(paramTpe, paramSym))))
      }
    }



  private def resolveParamsLists(
      ctx: BuilderContext
  )(params: List[List[Param]]): (BuilderContext, List[List[(c.Symbol, Provider)]]) =
    log.withBlock(s"Resolving params [${mkStringFrom2DimList(params)}]") {
      params.foldLeft((ctx, List.empty[List[(c.Symbol, Provider)]])) { case ((currentCtx, resolvedParamsLists), paramsList) =>
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
