package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._
import cats.implicits._

trait CatsProviders[C <: blackbox.Context] {
  val c: C
  val log: Logger

  val typeCheckUtil: TypeCheckUtil[c.type]

  trait Provider {
    def resultType: c.Type
    def dependencies: List[List[Option[Provider]]]
    def ident: c.Tree
    def value: c.Tree
  }

  class Effect(rawValue: c.Tree) extends Provider {
    import c.universe._

    lazy val resultType: Type = Effect.underlyingType(typeCheckUtil.typeCheckIfNeeded(rawValue))
    lazy val dependencies: List[List[Option[Provider]]] = List.empty
    lazy val asResource = new Resource(q"cats.effect.kernel.Resource.eval[cats.effect.IO, ${resultType}]($rawValue)")
    lazy val value: Tree = asResource.value
    lazy val ident: Tree = asResource.ident
  }

  object Effect {
    import c.universe._

    def underlyingType(tpe: Type): Type = tpe.typeArgs(0)
    def maybeUnderlyingType(tpe: Type): Option[Type] = if (isEffect(tpe)) Some(underlyingType(tpe)) else None
    def isEffect(tpe: Type): Boolean = tpe.typeSymbol.fullName.startsWith("cats.effect.IO") && tpe.typeArgs.size == 1
    def fromTree(tree: Tree, tpe: Type): Option[Effect] =
      if (isEffect(tpe)) Some(new Effect(tree))
      else None

  }

  class Resource(val value: c.Tree) extends Provider {
    import c.universe._

    lazy val resultType: Type = Resource.underlyingType(typeCheckUtil.typeCheckIfNeeded(value))
    lazy val dependencies: List[List[Option[Provider]]] = List.empty
    lazy val ident: Tree = Ident(TermName(c.freshName()))
  }

  object Resource {
    import c.universe._

    def underlyingType(tpe: Type): Type = tpe.typeArgs(1)
    def maybeUnderlyingType(tpe: Type): Option[Type] = if (isResource(tpe)) Some(underlyingType(tpe)) else None

    def isResource(tpe: Type): Boolean =
      tpe.typeSymbol.fullName.startsWith("cats.effect.kernel.Resource") && tpe.typeArgs.size == 2

    def fromTree(tree: Tree, tpe: Type): Option[Resource] =
      if (isResource(tpe)) Some(new Resource(tree))
      else None

  }

  class FactoryMethod(fun: c.Tree, val resultType: c.Type, val dependencies: List[List[Option[Provider]]])
      extends Provider {
    import c.universe._

    private lazy val appliedTree: Tree =
      log.withBlock(s"Applied tree for [$fun] from deps: [${dependencies.map(_.mkString(", ")).mkString(", ")}]") {
        dependencies.map(_.map(_.get.ident)).foldLeft(fun)((acc: Tree, args: List[Tree]) => Apply(acc, args))
      }
    lazy val result: Provider = log.withResult {
      val t = fun.symbol.asMethod.returnType

    //TODO support for FactoryMethods
      if (Resource.isResource(t)) new Resource(appliedTree) {
        override lazy val resultType: Type = Resource.underlyingType(t)
      }
      else if (Effect.isEffect(t)) new Effect(appliedTree) {
        override lazy val resultType: Type = Effect.underlyingType(t)
      }
      else new Instance(appliedTree)
    }(result => s"Factory method result [$result]")

    lazy val ident: Tree = log.withResult(result.ident)(i => s"Ident for [$fun] is [$i] object: [$this]")

    lazy val value: Tree = result.value

  }

  object FactoryMethod {
    import c.universe._

    def underlyingType(tree: Tree): Type = {
      val resultType = tree.symbol.asMethod.returnType
      (Resource.maybeUnderlyingType(resultType) orElse Effect.maybeUnderlyingType(resultType)).getOrElse(resultType)
    }

    def underlyingResultType(tree: Tree): Type = {
      val (fun, _) = fromTree(tree).getOrElse(c.abort(c.enclosingPosition, "TODO..."))
      underlyingType(fun)
    }

    def unapply(tree: Tree): Option[(Tree, List[ValDef])] = deconstruct(identity)(tree)
    def fromTree(tree: Tree): Option[(Tree, List[ValDef])] = unapply(tree)
    def isFactoryMethod(tree: Tree): Boolean = fromTree(tree).isDefined

    def deconstruct[T](componentsTransformer: ((Tree, List[ValDef])) => T)(tree: Tree): Option[T] = log.withBlock(s"DECONSTRUCT: [${showRaw(tree)}]")(tree match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some(componentsTransformer((f, p)))
      case Block(Nil, Function(p, Apply(f, _)))           => Some(componentsTransformer((f, p)))
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => Some(componentsTransformer((f, p)))
      case Function(p, Apply(f, _))           => Some(componentsTransformer((f, p)))
      // Other types not supported
      case _ => None
    })

  }

  case class Creator(
      resultType: c.Type,
      dependencies: List[List[Option[Provider]]],
      creatorFun: List[
        List[c.Tree]
      ] => c.Tree
  ) extends Provider {
    import c.universe._
    //TODO reuse created instance
    private lazy val appliedCreator = creatorFun(dependencies.map(_.map(_.get.ident)))
    lazy val asResource = new Resource(q"cats.effect.Resource.pure[cats.effect.IO, $resultType]($appliedCreator)")
    override lazy val ident = asResource.ident

    override def value: c.Tree = asResource.value
  }

  class Instance(val value: c.Tree) extends Provider {

    override def dependencies: List[List[Option[Provider]]] = List.empty

    lazy val resultType: c.Type = typeCheckUtil.typeCheckIfNeeded(value)
    lazy val ident: c.Tree = value

  }
}
