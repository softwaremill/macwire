package com.softwaremill.macwire.autocats.internals

import scala.reflect.macros.blackbox
import com.softwaremill.macwire.internals._

trait CatsProviders[C <: blackbox.Context] {
  val c: C
  val log: Logger

  val typeCheckUtil: TypeCheckUtil[c.type]

  sealed trait Provider {
    def resultType: c.Type
    def dependencies: List[List[(c.Symbol, Provider)]]
    def ident: c.Tree
    def value: c.Tree
    def symbol: c.Symbol
  }

  class Effect(rawValue: c.Tree) extends Provider {

    override def symbol: c.Symbol = rawValue.symbol

    import c.universe._

    lazy val resultType: Type = Effect.underlyingType(typeCheckUtil.typeCheckIfNeeded(rawValue))
    lazy val dependencies: List[List[(c.Symbol, Provider)]] = List.empty
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

    override def symbol: c.Symbol = value.symbol

    import c.universe._

    lazy val resultType: Type = Resource.underlyingType(typeCheckUtil.typeCheckIfNeeded(value))
    lazy val dependencies: List[List[(c.Symbol, Provider)]] = List.empty
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

  class FactoryMethod(
    val symbol: c.Symbol,
      methodType: c.Type,
      val resultType: c.Type,
      val dependencies: List[List[(c.Symbol, Provider)]],
      apply: List[List[c.Tree]] => c.Tree
  ) extends Provider {
    import c.universe._

    private lazy val appliedTree: Tree = apply(dependencies.map(_.map(_._2.ident)))
    
    lazy val result: Provider = log.withResult {
      val fmResultType = resultType
      //TODO support for FactoryMethods
      if (Resource.isResource(methodType)) new Resource(appliedTree) {
        override lazy val resultType: Type = Resource.underlyingType(methodType)
      }
      else if (Effect.isEffect(methodType)) new Effect(appliedTree) {
        override lazy val resultType: Type = Effect.underlyingType(methodType)
      }
      else
        new Resource(q"cats.effect.Resource.pure[cats.effect.IO, $resultType]($appliedTree)") {
          override lazy val resultType: Type = fmResultType
        }
    }(result => s"Factory method result [$result]")

    lazy val ident: Tree = result.ident

    lazy val value: Tree = result.value

  }

  object FactoryMethod {
    import c.universe._

    def underlyingType(tree: Tree): Type = {
      val resultType = tree.symbol.asMethod.returnType
      (Resource.maybeUnderlyingType(resultType) orElse Effect.maybeUnderlyingType(resultType)).getOrElse(resultType)
    }

    def underlyingResultType(tree: Tree): Type = {
      val (fun, _) =
        fromTree(tree).getOrElse(c.abort(c.enclosingPosition, s"The given tree is not a factory method: [$tree]"))
      underlyingType(fun)
    }

    def unapply(tree: Tree): Option[(Tree, List[ValDef])] = deconstruct(identity)(tree)
    def fromTree(tree: Tree): Option[(Tree, List[ValDef])] = unapply(tree)
    def isFactoryMethod(tree: Tree): Boolean = fromTree(tree).isDefined

    def deconstruct[T](componentsTransformer: ((Tree, List[ValDef])) => T)(tree: Tree): Option[T] = tree match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some(componentsTransformer((f, p)))
      case Block(Nil, Function(p, Apply(f, _)))           => Some(componentsTransformer((f, p)))
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => Some(componentsTransformer((f, p)))
      case Function(p, Apply(f, _))           => Some(componentsTransformer((f, p)))
      // Other types not supported
      case _ => None
    }

  }

  class Instance(val value: c.Tree) extends Provider {

    override def symbol: c.Symbol = value.symbol


    override def dependencies: List[List[(c.Symbol, Provider)]] = List.empty

    lazy val resultType: c.Type = typeCheckUtil.typeCheckIfNeeded(value)
    lazy val ident: c.Tree = value

  }

  class NotResolvedProvider(val resultType: c.Type, val symbol: c.Symbol) extends Provider {
    override def dependencies: List[List[(c.Symbol, Provider)]] = c.abort(c.enclosingPosition, s"Internal Error: Not resolved provider for type [$resultType]")
    override def ident: c.Tree = c.abort(c.enclosingPosition, s"Internal Error: Not resolved provider for type [$resultType]")
    override def value: c.Tree = c.abort(c.enclosingPosition, s"Internal Error: Not resolved provider for type [$resultType]")


  }
}
