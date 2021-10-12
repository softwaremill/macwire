package com.softwaremill.macwire
package catseffectsupport

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._

object MacwireCatsEffectMacros {
  private val log = new Logger()

  def wireApp_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._

    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

    trait Provider {
      def ident: Tree
      def `type`: Type
    }

    case class Resource(value: Tree) extends Provider {
      val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value).typeArgs(1)
      val ident: Tree = Ident(TermName(c.freshName()))
      lazy val tpe = typeCheckUtil.typeCheckIfNeeded(value)
    }

    case class Instance(value: Tree) extends Provider {
      lazy val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value)
      lazy val ident: Tree = value
    }

    def isResource(expr: Expr[Any]): Boolean = {
      val checkedType = typeCheckUtil.typeCheckIfNeeded(expr.tree)

      checkedType.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource") && checkedType.typeArgs.size == 2
    }
    
    val resourcesExprs = dependencies.filter(isResource)
    val instancesExprs = dependencies.filterNot(isResource)

    val resources = resourcesExprs
      .map(expr => Resource(expr.tree))
      .map(r => (r.`type`, r))
      .toMap

    val instances = instancesExprs
    .map(expr => Instance(expr.tree))
    .map(i => (i.`type`, i))
    .toMap

    log(s"resources: [${resources.mkString(", ")}]")
    log(s"instances: [${instances.mkString(", ")}]")

    def findInstance(t: Type): Option[Instance] = instances.get(t)

    def findResource(t: Type): Option[Resource] = resources.get(t)

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    def findeProvider(tpe: Type): Option[Provider] = findInstance(tpe).orElse(findResource(tpe))

    lazy val resolutionWithFallback: (Symbol, Type) => Tree = (_, tpe) =>
      if (isWireable(tpe)) findeProvider(tpe).map(_.ident).getOrElse(go(tpe))
      else c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")

    def go(t: Type): Tree = {

      val r =
        (ConstructorCrimper.constructorTree(c, log)(t, resolutionWithFallback) orElse CompanionCrimper
          .applyTree(c, log)(t, resolutionWithFallback)) getOrElse
          c.abort(c.enclosingPosition, s"Failed for [$t]")

      log(s"Constructed [$r]")
      r
    }

    val generatedInstance = go(targetType.tpe)

    val code =
      resources.values.foldRight(q"cats.effect.Resource.pure[cats.effect.IO, $targetType]($generatedInstance)") {
        case (resource, acc) =>
          q"${resource.value}.flatMap((${resource.ident}: ${resource.tpe}) => $acc)"
      }
    log(s"Code: [$code]")

    c.Expr[CatsResource[IO, T]](code)
  }

}
