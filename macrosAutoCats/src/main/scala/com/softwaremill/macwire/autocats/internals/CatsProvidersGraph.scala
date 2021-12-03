package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._
import scala.collection.immutable

class CatsProvidersGraph[C <: blackbox.Context](val c: C, log: Logger) {
  lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  type Resolver = c.universe.Type => Option[Provider]
  type FactoryMethodBuilder = (BuilderContext, Fallback) => (BuilderContext, FactoryMethod)
  type Fallback = (BuilderContext, c.universe.Type) => Option[(BuilderContext, Provider)]

  case class FactoryMethodTree(params: List[c.universe.ValDef], fun: c.Tree, resultType: c.Type) {}

  object FactoryMethodTree {
    import c.universe._

    def unapply(tree: Tree): Option[(List[c.universe.ValDef], c.universe.Tree)] =
      tree match {
        // Function with two parameter lists (implicit parameters) (<2.13)
        case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some((p, f))
        case Block(Nil, Function(p, Apply(f, _)))           => Some((p, f))
        // Function with two parameter lists (implicit parameters) (>=2.13)
        case Function(p, Apply(Apply(f, _), _)) => Some((p, f))
        case Function(p, Apply(f, _))           => Some((p, f))
        // Other types not supported
        case _ => None
      }

      def apply(tree: Tree): Option[FactoryMethodTree] =
        tree match {
          // Function with two parameter lists (implicit parameters) (<2.13)
          case Block(Nil, Function(p, Apply(Apply(f, _), _))) =>
            Some(FactoryMethodTree(p, f, FactoryMethod.underlyingType(f)))
          case Block(Nil, Function(p, Apply(f, _))) => Some(FactoryMethodTree(p, f, FactoryMethod.underlyingType(f)))
          // Function with two parameter lists (implicit parameters) (>=2.13)
          case Function(p, Apply(Apply(f, _), _)) => Some(FactoryMethodTree(p, f, FactoryMethod.underlyingType(f)))
          case Function(p, Apply(f, _))           => Some(FactoryMethodTree(p, f, FactoryMethod.underlyingType(f)))
          // Other types not supported
          case _ => None
        }
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

    def resolve(tpe: Type): Option[Either[Provider, FactoryMethodBuilder]] = ???
    def resolveV2(tpe: Type): Option[Either[Provider, FactoryMethodTree]] = {
      // val typeCheckedTpe = typeCheckUtil.typeCheckIfNeeded(tpe)

      val result = providers
        .get(tpe)
        .map(_.asLeft[FactoryMethodTree])
        .orElse(notResolvedFactoryMethods.get(tpe).map(_.asRight[Provider]))

      log(s"For type [$tpe] found [$result] in the current context")
      result
    }

    def nextFM(): (Type, FactoryMethodBuilder) = ???
    def next(): Option[FactoryMethodTree] = notResolvedFactoryMethods.headOption.map(_._2)
    def resolveProvider(tpe: Type): Option[Provider] = ???
    def addProvider(provider: Provider): BuilderContext = ???
  }

  trait Provider {
    def resultType: c.Type
    def dependencies: List[Option[Provider]]
    def ident: c.Tree
    def value: c.Tree
  }

  class Effect(rawValue: c.Tree) extends Provider {
    import c.universe._

    override def resultType: Type = Effect.underlyingType(typeCheckUtil.typeCheckIfNeeded(rawValue))

    override def dependencies: List[Option[Provider]] = List.empty

    lazy val asResource = new Resource(q"cats.effect.kernel.Resource.eval[cats.effect.IO, ${resultType}]($rawValue)") 

    lazy val value: Tree = asResource.value
    lazy val ident: Tree = asResource.ident
  }

  object Effect {
    import c.universe._
    def fromTree(tree: Tree): Option[Effect] =
      if (isEffect(typeCheckUtil.typeCheckIfNeeded(tree))) Some(new Effect(tree))
      else None

    def underlyingType(tpe: Type): Type = tpe.typeArgs(0)
    def maybeUnderlyingType(tpe: Type): Option[Type] = if (isEffect(tpe)) Some(underlyingType(tpe)) else None

    def isEffect(tpe: Type): Boolean =
      tpe.typeSymbol.fullName.startsWith("cats.effect.IO") && tpe.typeArgs.size == 1

  }

  class Resource(val value: c.Tree) extends Provider {
    import c.universe._
    override def resultType: Type = Resource.underlyingType(typeCheckUtil.typeCheckIfNeeded(value))

    override def dependencies: List[Option[Provider]] = List.empty

    override lazy val ident: Tree = Ident(TermName(c.freshName()))
  }

  object Resource {
    import c.universe._
    def underlyingType(tpe: Type): Type = tpe.typeArgs(1)
    def maybeUnderlyingType(tpe: Type): Option[Type] = if (isResource(tpe)) Some(underlyingType(tpe)) else None

    def isResource(tpe: Type): Boolean =
      tpe.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource") && tpe.typeArgs.size == 2

  }

  case class FactoryMethod(fun: c.Tree, resultType: c.Type, dependencies: List[Option[Provider]]) extends Provider {
    import c.universe._

    lazy val appliedTree: Tree = {
      val values = dependencies.map(_.get.ident)
      
      q"$fun(..$values)"
    }

    private lazy val result: Provider = {
      log(s"APPLIED TREE: [$appliedTree]")
      val t = fun.symbol.asMethod.returnType

      if (Resource.isResource(t)) new Resource(appliedTree)
      else if (Effect.isEffect(t)) new Effect(appliedTree)
      else new Instance(appliedTree)

    }

    lazy val ident: Tree = result.ident
    lazy val value: Tree = result.value

  }

  object FactoryMethod {
    import c.universe._

    def maybeBuildDependencies(ctx: BuilderContext)(factoryMethodTree: Tree): List[Option[Provider]] = {
      val (params, fun) = fromTree(factoryMethodTree).getOrElse(c.abort(c.enclosingPosition, "???"))

      params.map(p => ctx.resolveProvider(p.tpe))
    }

    def apply(ctx: BuilderContext, fallback: Fallback)(tree: Tree): (BuilderContext, FactoryMethod) = {
      val (params, fun) = fromTree(tree).getOrElse(c.abort(c.enclosingPosition, "???"))

      val (updatedCtx, deps) = params.foldLeft((ctx, List.empty[Option[Provider]])) {
        case ((currentCtx, resultDeps), param) =>
          currentCtx.resolve(param.tpe) match {
            case Some(Right(fmBuilder)) => {
              val (updatedContext, p) = fmBuilder(currentCtx, fallback)
              (updatedContext.resolvedFactoryMethod(p), resultDeps :+ Some(p.asInstanceOf[Provider]))
            }
            case Some(Left(provider)) => (currentCtx, resultDeps :+ Some(provider.asInstanceOf[Provider]))

            case None =>
              fallback(currentCtx, param.tpe) match {
                case Some((updatedContext, provider)) => (updatedContext, resultDeps :+ Some(provider))
                case None                             => (currentCtx, resultDeps :+ None)
              }
          }
      }

      println(s"FUN [$fun]")
      println(s"PARAMS [$params]")

      // (
      //   updatedCtx,
      //   FactoryMethod(
      //     resultType = underlyingType(fun),
      //     resultType = FactoryMethod.underlyingType(tree),
      //     dependencies = deps
      //   )
      // )
      ???
    }

    def underlyingType(tree: Tree): Type = {
      val resultType = tree.symbol.asMethod.returnType
      (Resource.maybeUnderlyingType(resultType) orElse Effect.maybeUnderlyingType(resultType)).getOrElse(resultType)
    }

    def underlyingResultType(tree: Tree): Type = {
      val (_, fun) = fromTree(tree).getOrElse(c.abort(c.enclosingPosition, "TODO..."))
      underlyingType(fun)
    }

    def unapply(tree: Tree): Option[(List[c.universe.ValDef], c.universe.Tree)] =
      tree match {
        // Function with two parameter lists (implicit parameters) (<2.13)
        case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some((p, f))
        case Block(Nil, Function(p, Apply(f, _)))           => Some((p, f))
        // Function with two parameter lists (implicit parameters) (>=2.13)
        case Function(p, Apply(Apply(f, _), _)) => Some((p, f))
        case Function(p, Apply(f, _))           => Some((p, f))
        // Other types not supported
        case _ => None
      }

    def fromTree(tree: Tree): Option[(List[c.universe.ValDef], c.universe.Tree)] = unapply(tree)

    def isFactoryMethod(tree: Tree): Boolean = fromTree(tree).isDefined

  }
//FIXME I don't really like the name `creator`, but don't know a better one ATM
  case class Constructor(
      resultType: c.Type,
      dependencies: List[Option[Provider]],
      creator: List[
        List[c.Tree]
      ] => c.Tree /* hmmm not sure yet how to generate the result tree since we need to support multi-params lists*/
  ) extends Provider {
    override lazy val ident = ???
    override def value: c.Tree = ???
  }

  class Instance(val value: c.Tree) extends Provider {

    override def dependencies: List[Option[Provider]] = List.empty

    lazy val resultType: c.Type = typeCheckUtil.typeCheckIfNeeded(value)
    lazy val ident: c.Tree = value
    
  }

  def buildGraphVertices(rawProviders: List[c.universe.Expr[Any]]): List[Provider] = {
    import c.universe._
    import cats.implicits._

    //O(n)
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

    /** Resolve factory methods
      */
    def resolveFactoryMethods(initialCtx: BuilderContext): BuilderContext = {
      //select next FM to be buit
      // val (tpe, builder) = initialCtx.nextFM()

      // def resolveParamsList(ctx: BuilderContext)()
      def goParams(ctx: BuilderContext)(params: List[List[Type]]): (BuilderContext, List[List[Option[Provider]]]) =
        log.withBlock(s"Resolving params [${mkStringFrom2DimList(params)}]") {
          params.foldLeft((ctx, List.empty[List[Option[Provider]]])) { case ((ctx2, llp), params2) =>
            val (updatedCtx, r1) = params2.foldLeft((ctx2, List.empty[Option[Provider]])) {
              case ((ctx3, resolvedParams), param) =>
                ctx3.resolveV2(param) match {
                  case Some(Left(provider)) => (ctx3, resolvedParams :+ Some(provider))
                  case Some(Right(fmt)) => {
                    val (updatedCtx, fm) = go(ctx3)(fmt)

                    (updatedCtx, resolvedParams :+ Some(fm))
                  }
                  case None => {
                    ConstructorCrimper
                      .constructorV3(c, log)(param)
                      .map { case (constructorParams, creatorF) =>
                        val (updatedCtx, resolvedConParams) =
                          goParams(ctx)(constructorParams.map(_.map(s => ConstructorCrimper.paramType(c)(param, s))))
                        val con = Constructor(param, resolvedConParams.flatten, creatorF)
                        (updatedCtx.addProvider(con), resolvedParams :+ Some(con))
                      }
                      .getOrElse((ctx, resolvedParams :+ None))
                  }
                }
            }
            (updatedCtx, llp :+ r1)
          }
        }

      def go(ctx: BuilderContext)(fmt: FactoryMethodTree): (BuilderContext, FactoryMethod) = {
        val FactoryMethodTree(params, fun, resultType) = fmt
        log.withBlock(s"Resolving [$fun]") {
          //Traverse the list of required params
          val (updatedCtx, deps) = goParams(ctx)(List(params.map { case ValDef(_, name, tpt, rhs) =>
            typeCheckUtil.typeCheckIfNeeded(tpt)
          }))
          log(s"Resolved dependencies [${deps.map(_.mkString(", ")).mkString("\n")}]")

          (
            updatedCtx,
            FactoryMethod(
              fun = fun,
              resultType = resultType,
              dependencies = deps.flatten // FIXME!!!
            )
          )
        }
      }

      def go2(ctx: BuilderContext): BuilderContext = ctx.next() match {
        case None => ctx
        case Some(fmt: FactoryMethodTree) => {
          val (updatedCtx, fm) = go(ctx)(fmt)

          go2(updatedCtx.resolvedFactoryMethod(fm))
        }
        case Some(v) => c.abort(c.enclosingPosition, s"Internal error. Expected FactoryMethodTree, but found [$v]")
      }

      go2(initialCtx)
    }

    val resolvedCtx = resolveFactoryMethods(BuilderContext(providers, fms))
    log(s"Providers: [${resolvedCtx.providers.values.mkString(", ")}]")

    val inputProvidersOrder = rawProviders.map(expr => {
      val tree = expr.tree
      val tpe = typeCheckUtil.typeCheckIfNeeded(tree)
      if (FactoryMethod.isFactoryMethod(tree)) FactoryMethod.underlyingResultType(tree)
      else if (Resource.isResource(tpe)) Resource.underlyingType(tpe)
      else if (Effect.isEffect(tpe)) Effect.underlyingType(tpe)
      else tpe
    })

    log(s"Input providers order [${inputProvidersOrder.mkString(", ")}]")
    
    def buildProvidersList(ctx: BuilderContext)(remainingInputTypes: List[Type], resultProviders: List[Provider]): (List[Type], List[Provider]) = remainingInputTypes match {
      case Nil => (remainingInputTypes, resultProviders)
      case head :: tl => log.withBlock(s"Build providers list from [$head]"){
        def goDeep(provider: Provider): List[Provider] = log.withBlock(s"Building deeper with [$provider]"){
          provider.dependencies match {
            case Nil => List(provider)
            case deps => deps.foldl(List(provider)) {
              case (r, None) => r
              case (r, Some(p)) if resultProviders.contains(p) => r
              case (r, Some(p)) => goDeep(p) ++ r
            }
          }
        }
        val ps = goDeep(ctx.providers.get(head).getOrElse(c.abort(c.enclosingPosition, "Internal error. Missing provider")))
        buildProvidersList(ctx)(remainingInputTypes.diff(resultProviders.map(_.resultType) ++ ps.map(_.resultType)), resultProviders ++ ps)
      }
    }

    val (tps, orderedProviders) = buildProvidersList(resolvedCtx)(inputProvidersOrder, List.empty)
    log(s"Ordered providers [${orderedProviders.map(_.resultType).mkString(", ")}]")

    orderedProviders
  }

  private def mkStringFrom2DimList[T](ll: List[List[T]]): String = ll.map(_.mkString(", ")).mkString("\n")
}
