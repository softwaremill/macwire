package com.softwaremill.macwire.autocats

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._
import cats.implicits._
import com.softwaremill.macwire.MacwireMacros
import com.softwaremill.macwire.autocats.internals._

object MacwireCatsEffectMacros {
  private val log = new Logger()

  def autowire2_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._

    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
    val graph = new CatsProvidersGraphContext[c.type](c, log)

    val sortedProviders = graph.buildGraphVertices(dependencies.toList).topologicalOrder()

    log(s"Sorted providers [${sortedProviders.mkString(", ")}]")
    val code = sortedProviders.map {
      case fm: graph.FactoryMethod => fm.result
      case p => p
    }.collect {
      case e: graph.Effect => e
      case r: graph.Resource => r
    }.foldRight(
      q"cats.effect.Resource.pure[cats.effect.IO, $targetType](com.softwaremill.macwire.autowire[$targetType](..${sortedProviders.map(_.ident)}))"
      // q"cats.effect.Resource.pure[cats.effect.IO, $targetType](???)"
    ) { case (resource, acc) =>
      q"${resource.value}.flatMap((${resource.ident}: ${resource.resultType}) => $acc)"
    }
    log(s"CODE: [$code]")
   
    c.Expr[CatsResource[IO, T]](code)
  }

  def autowire_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._

    type Resolver = (Symbol, Type) => Tree

    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

    val providersEntities = new CatsProviders[c.type](c, log)
    import providersEntities._

    def providerFromExpr(expr: Expr[Any]): Provider = {
      val tree = expr.tree
      (Resource.fromTree(tree) orElse Effect.fromTree(tree) orElse CatsFactoryMethod.fromTree(tree) orElse FactoryMethod.fromTree(tree))
        .getOrElse(new Instance(tree))
    }

    def findProviderIn(values: Seq[Value])(tpe: Type): Option[Tree] = {
     log(s"Looking for a [$tpe]")
     val v= values.find(_.`type` <:< tpe).map(_.ident)
     log(s"Found [$v]")
     v
    }

    val providers = dependencies.map(providerFromExpr)
log(s"Providers: [${providers.mkString(", ")}]")
    
    val values: Seq[Value] = {

      val (fms, initValues): (Vector[FactoryMethod], Vector[Value]) = providers.toVector.partitionBifold {
        case e: Effect => Right(e.asResource)
        case v: Value  => Right(v)
        case fm: CatsFactoryMethod        => Left(fm)
        case fm: FactoryMethod        => Left(fm)
      }


      def freshInstanceFromEmptyConstructor(t: Type): Option[Tree] = {
        (ConstructorCrimper.constructorTreeV2(c, log)(t, (_, _) => None) orElse CompanionCrimper
        .applyTreeV2(c, log)(t, (_, _) => None))
      }

      def go(fms: Vector[FactoryMethod], values: Vector[Value]): Vector[Value] = fms match {
        case Vector() => values
        case v => {
          log(s"Resolving for FMs [${fms.mkString(", ")}] with values [${values.mkString(", ")}]")
          //FIXME use here catsFactoryMethod?
          val (remainingFms, appliedFms) = v.partitionBifold(f => f.maybeResult(t => findProviderIn(values)(t).orElse(freshInstanceFromEmptyConstructor(t))).toRight(f))
          println(s"RFMS: [${remainingFms.mkString(", ")}]")
          println(s"AFMS: [${appliedFms.mkString(", ")}]")
          if (appliedFms.isEmpty) c.abort(c.enclosingPosition, "Failed to apply any factory method")

          go(remainingFms, values ++ appliedFms)
        
      }
      }

      go(fms, initValues)
    }

    log(s"exprs: s[${dependencies.mkString(", ")}]")
    
    log(s"Values: [${values.mkString(", ")}]")

    def findProvider(tpe: Type): Option[Tree] = findProviderIn(values)(tpe)

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    lazy val resolutionWithFallback: (Symbol, Type) => Tree = (_, tpe) =>
      if (isWireable(tpe)) findProvider(tpe).getOrElse(go(tpe))
      else c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")

    def go(t: Type): Tree = {

      val r =
        (ConstructorCrimper.constructorTree(c, log)(t, resolutionWithFallback) orElse CompanionCrimper
          .applyTree(c, log)(t, resolutionWithFallback)) getOrElse
          c.abort(c.enclosingPosition, s"Failed for [$t]")

      log(s"Constructed [$r]")
      r
    }

    val resources: Seq[Resource] = values.collect {
      case r: Resource => r
      case e: Effect   => e.asResource
    }

    val code = resources.foldRight(
      q"cats.effect.Resource.pure[cats.effect.IO, $targetType](com.softwaremill.macwire.autowire[$targetType](..${values
        .map(_.ident)}))"
    ) { case (resource, acc) =>
      q"${resource.value}.flatMap((${resource.ident}: ${resource.`type`}) => $acc)"
    }
    log(s"Code: [$code]")

    c.Expr[CatsResource[IO, T]](code)
  }

}
