package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._
import scala.collection.immutable

class CatsProvidersGraphContext[C <: blackbox.Context](val c: C, val log: Logger) extends CatsProvidersV2[C] with GraphBuilderUtils[C] {
  lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
  

  class CatsProvidersGraph(providers: Map[c.Type, Provider], inputTypesOrder: List[c.Type]) {
    import c.universe._


    def topologicalOrder(): List[Provider] = {
      def go(remainingInputTypes: List[Type], resultProviders: List[Provider]): (List[Type], List[Provider]) =       
        remainingInputTypes match {
        case Nil => (remainingInputTypes, resultProviders)
        case head :: tl =>
          log.withBlock(s"Build providers list from [$head]") {
            def goDeep(provider: Provider): List[Provider] = log.withBlock(s"Building deeper with [$provider]") {
              provider.dependencies.flatten match { //FIXME cannot just flatten it :)
                case Nil => List(provider)
                case deps =>
                  deps.foldl(List(provider)) {
                    case (r, None)                                   => r
                    case (r, Some(p)) if resultProviders.contains(p) => r
                    case (r, Some(p))                                => goDeep(p) ++ r
                  }
              }
            }
            val ps = goDeep(
              providers.get(head).getOrElse(c.abort(c.enclosingPosition, "Internal error. Missing provider"))
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

    def resolveFactoryMethods(initialCtx: BuilderContext): BuilderContext = {
      def goParams(ctx: BuilderContext)(params: List[List[Type]]): (BuilderContext, List[List[Option[Provider]]]) =
        log.withBlock(s"Resolving params [${mkStringFrom2DimList(params)}]") {
          params.foldLeft((ctx, List.empty[List[Option[Provider]]])) { case ((ctx2, llp), params2) =>
            val (updatedCtx, r1) = params2.foldLeft((ctx2, List.empty[Option[Provider]])) {
              case ((ctx3, resolvedParams), param) =>
                ctx3.resolve(param) match {
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
                        val con = Constructor(param, resolvedConParams, creatorF)
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
            new FactoryMethod(
              fun = fun,
              resultType = resultType,
              dependencies = deps
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

    new CatsProvidersGraph(resolvedCtx.providers, inputProvidersOrder)
  }

  private def mkStringFrom2DimList[T](ll: List[List[T]]): String = ll.map(_.mkString(", ")).mkString("\n")
}
