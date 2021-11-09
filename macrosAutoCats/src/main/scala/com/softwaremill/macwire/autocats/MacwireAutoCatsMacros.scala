package com.softwaremill.macwire.autocats

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

    val providersEntities = new Providers[c.type](c)
    import providersEntities._

    def providerFromExpr(expr: Expr[Any]): Provider = {
      val tree = expr.tree
      (Resource.fromTree(tree) orElse Effect.fromTree(tree) orElse FactoryMethod.fromTree(tree))
        .getOrElse(new Instance(tree))
    }

    def findProviderIn(values: Seq[Value])(tpe: Type): Option[Tree] = values.find(_.`type` <:< tpe).map(_.ident)

    val providers = dependencies.map(providerFromExpr)
    val values: Seq[Value] = {
      val init: Seq[Value] = providers.flatMap {
        case e: Effect => Some(e.asResource)
        case v: Value  => Some(v)
        case _         => None
      }

      providers
        .foldLeft(init) {
          case (v, fm: FactoryMethod) => {
            v :+ fm.result(x =>
              findProviderIn(v)(x).getOrElse(q"com.softwaremill.macwire.autowire[$x](..${v.map(_.ident)})")
            )
          }
          case (v, _) => v
        }
        .collect { case v: Value =>
          v
        }
    }

    log(s"exprs: s[${dependencies.mkString(", ")}]")
    log(s"Providers: [${providers.mkString(", ")}]")
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

  class Providers[C <: blackbox.Context](val con: C) {
    import con.universe._

    lazy val typeCheckUtil = new TypeCheckUtil[con.type](con, log)

    sealed trait Provider {
      def `type`: Type
    }

    sealed trait Value extends Provider {
      def ident: Tree
    }

    class Resource(valueL: => Tree) extends Value {
      lazy val value = valueL

      lazy val `type`: Type =
        Resource.underlyingType(typeCheckUtil.typeCheckIfNeeded(value)).getOrElse(con.abort(con.enclosingPosition, "TODO"))
      lazy val ident: Tree = Ident(TermName(con.freshName()))
      // lazy val tpe = typeCheckUtil.typeCheckIfNeeded(value)

    }

    object Resource {
      def fromTree(tree: Tree): Option[Resource] =
        if (isResource(typeCheckUtil.typeCheckIfNeeded(tree))) Some(new Resource(tree))
        else None

      def underlyingType(tpe: Type): Option[Type] = if (isResource(tpe)) Some(tpe.typeArgs(1)) else None

      def isResource(tpe: Type): Boolean =
        tpe.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource") && tpe.typeArgs.size == 2

    }

    case class Instance(value: Tree) extends Value {
      lazy val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value)
      lazy val ident: Tree = value
    }

    class FactoryMethod(params: List[ValDef], fun: Tree) extends Provider {

      def result(resolver: Type => Tree): Value = {

        val resultTree = applyWith(resolver)

        val t = fun.symbol.asMethod.returnType
        if (Resource.isResource(t)) new Resource(resultTree) {
          override lazy val `type`: Type = tt
        }
        else if (Effect.isEffect(t)) new Effect(resultTree) {
          override lazy val `type`: Type = tt
        }
        else Instance(resultTree)

      }

      lazy val `type`: Type = {
        val resultType = fun.symbol.asMethod.returnType
        (Resource.underlyingType(resultType) orElse Effect.underlyingType(resultType)).getOrElse(resultType)
      }
      lazy val tt = `type`
      def applyWith(resolver: Type => Tree): Tree = {
        val values = params.map { case ValDef(_, name, tpt, rhs) =>
          resolver(typeCheckUtil.typeCheckIfNeeded(tpt))
        }

        q"$fun(..$values)"
      }

    }

    object FactoryMethod {
      def fromTree(tree: Tree): Option[FactoryMethod] = tree match {
        // Function with two parameter lists (implicit parameters) (<2.13)
        case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some(new FactoryMethod(p, f))
        case Block(Nil, Function(p, Apply(f, _)))           => Some(new FactoryMethod(p, f))
        // Function with two parameter lists (implicit parameters) (>=2.13)
        case Function(p, Apply(Apply(f, _), _)) => Some(new FactoryMethod(p, f))
        case Function(p, Apply(f, _))           => Some(new FactoryMethod(p, f))
        // Other types not supported
        case _ => None
      }

    }

    class Effect(value: Tree) extends Value {

      override def ident: Tree = asResource.ident //????

      override lazy val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value).typeArgs(0)

      def ttt = `type`
      lazy val asResource = new Resource(q"cats.effect.kernel.Resource.eval[cats.effect.IO, ${`type`}]($value)") {
        override lazy val `type`: Type = ttt
      }
    }

    object Effect {
      def fromTree(tree: Tree): Option[Effect] =
        if (isEffect(typeCheckUtil.typeCheckIfNeeded(tree))) Some(new Effect(tree))
        else None

      def underlyingType(tpe: Type): Option[Type] = if (isEffect(tpe)) Some(tpe.typeArgs(0)) else None

      def isEffect(tpe: Type): Boolean =
        tpe.typeSymbol.fullName.startsWith("cats.effect.IO") && tpe.typeArgs.size == 1

    }

  }

  // def autowirepure_impl[T: c.WeakTypeTag](
  //     c: blackbox.Context
  // )(dependencies: c.Expr[Any]*): c.Expr[T] = {
  //   import c.universe._

  //   val targetType = implicitly[c.WeakTypeTag[T]]
  //   lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  //   sealed trait Provider {
  //     def `type`: Type
  //   }

  //   case class Instance(value: Tree) extends Provider {
  //     lazy val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value)
  //     lazy val ident: Tree = value
  //   }

  //   class FactoryMethod(params: List[ValDef], fun: Tree) extends Provider {

  //     def result(resolver: Type => Tree): Instance = new Instance(applyWith(resolver))

  //     lazy val `type`: Type = fun.symbol.asMethod.returnType

  //     def applyWith(resolver: Type => Tree): Tree = {
  //       val values = params.map { case ValDef(_, name, tpt, rhs) =>
  //         resolver(typeCheckUtil.typeCheckIfNeeded(tpt))
  //       }

  //       q"$fun(..$values)"
  //     }

  //   }

  //   object FactoryMethod {
  //     def fromTree(tree: Tree): Option[FactoryMethod] = tree match {
  //       // Function with two parameter lists (implicit parameters) (<2.13)
  //       case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some(new FactoryMethod(p, f))
  //       case Block(Nil, Function(p, Apply(f, _)))           => Some(new FactoryMethod(p, f))
  //       // Function with two parameter lists (implicit parameters) (>=2.13)
  //       case Function(p, Apply(Apply(f, _), _)) => Some(new FactoryMethod(p, f))
  //       case Function(p, Apply(f, _))           => Some(new FactoryMethod(p, f))
  //       // Other types not supported
  //       case _ => None
  //     }

  //   }

  //   def providerFromExpr(expr: Expr[Any]): Provider = {
  //     val tree = expr.tree
  //     FactoryMethod
  //       .fromTree(tree)
  //       .getOrElse(new Instance(tree))
  //   }

  //   val providers = dependencies.map(providerFromExpr)

  //   log(s"PURE exprs: s[${dependencies.mkString(", ")}]")
  //   log(s"PURE Providers: [${providers.mkString(", ")}]")
  //   log(s"PURE ProvidersTYPES: [${providers.map(_.`type`).mkString(", ")}]")

  //   def findProvider(tpe: Type): Option[Tree] = providers.find(_.`type` <:< tpe).map {
  //     case i: Instance       => i.ident
  //     case fm: FactoryMethod => fm.result(findProvider(_).getOrElse(c.abort(c.enclosingPosition, "TODO3"))).ident
  //   }

  //   val code = go(targetType.tpe)
  //   log(s"Code: [$code]")

  //   c.Expr[T](code)
  // }

  // private def wireWithResolver[T: c.WeakTypeTag](
  //     c: blackbox.Context
  // )(resolver: c.Type => Option[c.Tree]) = {
  //   import c.universe._

  //   def isWireable(tpe: Type): Boolean = {
  //     val name = tpe.typeSymbol.fullName

  //     !name.startsWith("java.lang.") && !name.startsWith("scala.")
  //   }

  //   lazy val resolutionWithFallback: (Symbol, Type) => Tree = (_, tpe) =>
  //     if (isWireable(tpe)) resolver(tpe).orElse(go(tpe)).getOrElse(c.abort(c.enclosingPosition, s"TODO???"))
  //     else c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")

  //   def go(t: Type): Option[Tree] =
  //     (ConstructorCrimper.constructorTree(c, log)(t, resolutionWithFallback) orElse CompanionCrimper
  //       .applyTree(c, log)(t, resolutionWithFallback))


  // }
}
