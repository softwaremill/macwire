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

    case class Resource(value: Tree) {
      lazy val resourceType = typeCheckUtil.typeCheckIfNeeded(value).typeArgs(1)
      lazy val ident = Ident(TermName(c.freshName()))
      lazy val tpe = typeCheckUtil.typeCheckIfNeeded(value)
    }

    val resources = dependencies
      .map { expr =>
        val checkedType = typeCheckUtil.typeCheckIfNeeded(expr.tree)

        if (!checkedType.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource"))
          c.abort(c.enclosingPosition, s"Unsupported resource type [$checkedType].")
        else if (checkedType.typeArgs.size != 2)
          c.abort(c.enclosingPosition, s"Expected 2 type args, but found [${checkedType.typeArgs.size}].")
        else Resource(expr.tree)
      }
      .map(r => (r.resourceType, r))
      .toMap

    log(s"RESOURCES: [${resources.mkString(", ")}]")

    def findResource(t: Type): Option[Resource] = resources.get(t)

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    lazy val resolutionFallback: (c.Symbol, c.Type) => c.Tree = (_, tpe) =>
      if (isWireable(tpe)) findResource(tpe).map(_.ident).getOrElse(go(tpe))
      else c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")

    def go(t: Type): Tree = {

      val r =
        (ConstructorCrimper.constructorTree(c, log)(t, resolutionFallback) orElse CompanionCrimper
          .applyTree(c, log)(t, resolutionFallback)) getOrElse
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
