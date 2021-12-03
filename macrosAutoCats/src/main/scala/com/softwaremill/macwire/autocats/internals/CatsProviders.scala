package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import cats.implicits._

import com.softwaremill.macwire.internals._

class CatsProviders[C <: blackbox.Context](c: C, log: Logger) extends Providers(c, log) {
  import con.universe._

  class Resource(valueL: => Tree) extends Value {
    lazy val value = valueL

    lazy val `type`: Type =
      Resource
        .underlyingType(typeCheckUtil.typeCheckIfNeeded(value))
        .getOrElse(con.abort(con.enclosingPosition, "TODO"))
    lazy val ident: Tree = Ident(TermName(con.freshName()))

  }

  object Resource {
    def fromTree(tree: Tree): Option[Resource] =
      if (isResource(typeCheckUtil.typeCheckIfNeeded(tree))) Some(new Resource(tree))
      else None

    def underlyingType(tpe: Type): Option[Type] = if (isResource(tpe)) Some(tpe.typeArgs(1)) else None

    def isResource(tpe: Type): Boolean =
      tpe.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource") && tpe.typeArgs.size == 2

  }

  class CatsFactoryMethod(params: List[ValDef], fun: Tree) extends FactoryMethod(params, fun) {

    override def result(resolver: Type => Tree): Value = {

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

    override def maybeResult(resolver: Type => Option[Tree]): Option[Value] = {
      println(s"CATS BASE METHOD")
      maybeApplyWith(resolver).map { resultTree =>
        val t = fun.symbol.asMethod.returnType
        if (Resource.isResource(t)) new Resource(resultTree) {
          override lazy val `type`: Type = tt
        }
        else if (Effect.isEffect(t)) new Effect(resultTree) {
          override lazy val `type`: Type = tt
        }
        else Instance(resultTree)

      }
    }
    override lazy val `type`: Type = {
      val resultType = fun.symbol.asMethod.returnType
      (Resource.underlyingType(resultType) orElse Effect.underlyingType(resultType)).getOrElse(resultType)
    }
    lazy val tt = `type`
    override def applyWith(resolver: Type => Tree): Tree = {
      val values = params.map { case ValDef(_, name, tpt, rhs) =>
        resolver(typeCheckUtil.typeCheckIfNeeded(tpt))
      }

      q"$fun(..$values)"
    }

    override def maybeApplyWith(resolver: Type => Option[Tree]): Option[Tree] =
      params
        .map { case ValDef(_, name, tpt, rhs) =>
          resolver(typeCheckUtil.typeCheckIfNeeded(tpt))
        }
        .sequence
        .map(values => q"$fun(..$values)")

  }

  object CatsFactoryMethod {
    def fromTree(tree: Tree): Option[CatsFactoryMethod] = tree match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some(new CatsFactoryMethod(p, f))
      case Block(Nil, Function(p, Apply(f, _)))           => Some(new CatsFactoryMethod(p, f))
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => Some(new CatsFactoryMethod(p, f))
      case Function(p, Apply(f, _))           => Some(new CatsFactoryMethod(p, f))
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
