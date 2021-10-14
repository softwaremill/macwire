package com.softwaremill.macwire
package catseffectsupport

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._

object MacwireCatsEffectMacros {
  private val log = new Logger()

  def autowire_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._

    type Resolver = (Symbol, Type) => Tree

    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

    trait Provider {
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

    case class FactoryMethod(value: Tree) extends Provider {
      val (params, fun) = value match {
        // Function with two parameter lists (implicit parameters) (<2.13)
        case Block(Nil, Function(p, Apply(Apply(f, _), _))) => (p, f)
        case Block(Nil, Function(p, Apply(f, _)))           => (p, f)
        // Function with two parameter lists (implicit parameters) (>=2.13)
        case Function(p, Apply(Apply(f, _), _)) => (p, f)
        case Function(p, Apply(f, _))           => (p, f)
        // Other types not supported
        case _ => c.abort(c.enclosingPosition, s"Not supported factory type: [$value]")
      }

      lazy val `type`: Type = fun.symbol.asMethod.returnType

      def applyWith(resolver: Resolver): Tree = {
        val values = params.map { case vd @ ValDef(_, name, tpt, rhs) =>
          resolver(vd.symbol, typeCheckUtil.typeCheckIfNeeded(tpt))
        }

        q"$fun(..$values)"
      }

    }

    case class Effect(value: Tree) extends Provider {

      override val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value).typeArgs(0)

      lazy val asResource = Resource(q"cats.effect.kernel.Resource.eval[cats.effect.IO, ${`type`}]($value)")
    }

    def isResource(expr: Expr[Any]): Boolean = {
      val checkedType = typeCheckUtil.typeCheckIfNeeded(expr.tree)

      checkedType.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource") && checkedType.typeArgs.size == 2
    }

    def isFactoryMethod(expr: Expr[Any]): Boolean = expr.tree match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => true
      case Block(Nil, Function(p, Apply(f, _)))           => true
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => true
      case Function(p, Apply(f, _))           => true
      // Other types not supported
      case _ => false
    }

    def isEffect(expr: Expr[Any]): Boolean = {
      val checkedType = typeCheckUtil.typeCheckIfNeeded(expr.tree)

      checkedType.typeSymbol.fullName.startsWith("cats.effect.IO") && checkedType.typeArgs.size == 1
    }

    val resourcesExprs = dependencies.filter(isResource)
    val factoryMethodsExprs = dependencies.filter(isFactoryMethod)
    val effectsExprs = dependencies.filter(isEffect)
    val instancesExprs = dependencies.diff(resourcesExprs).diff(factoryMethodsExprs).diff(effectsExprs)

    val resources =
      (effectsExprs.map(expr => Effect(expr.tree).asResource) ++ resourcesExprs.map(expr => Resource(expr.tree)))
        .map(r => (r.`type`, r))
        .toMap

    val instances = instancesExprs
      .map(expr => Instance(expr.tree))
      .map(i => (i.`type`, i))
      .toMap

    val factoryMethods = factoryMethodsExprs
      .map(expr => FactoryMethod(expr.tree))
      .map(i => (i.`type`, i))
      .toMap

    log(s"exprs: s[${dependencies.mkString(", ")}]")
    log(s"resources: [${resources.mkString(", ")}]")
    log(s"instances: [${instances.mkString(", ")}]")
    log(s"factory methods: [${factoryMethods.mkString(", ")}]")

    def findInstance(t: Type): Option[Instance] = instances.get(t)

    def findResource(t: Type): Option[Resource] = resources.get(t)
    def findFactoryMethod(t: Type): Option[FactoryMethod] = factoryMethods.get(t)

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    def findeProvider(tpe: Type): Option[Tree] = findInstance(tpe)
      .map(_.ident)
      .orElse(findResource(tpe).map(_.ident))
      .orElse(findFactoryMethod(tpe).map(_.applyWith(resolutionWithFallback)))

    lazy val resolutionWithFallback: (Symbol, Type) => Tree = (_, tpe) =>
      if (isWireable(tpe)) findeProvider(tpe).getOrElse(go(tpe))
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
