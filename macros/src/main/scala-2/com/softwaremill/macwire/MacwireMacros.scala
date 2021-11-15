package com.softwaremill.macwire

import com.softwaremill.macwire.internals._

import scala.reflect.macros.blackbox

object MacwireMacros {
  private val log = new Logger()

  def wire_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] =
    wire(c)(DependencyResolver.throwErrorOnResolutionFailure[c.type, c.universe.Type, c.universe.Tree](c, log))

  def wireRec_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] = {
    import c.universe._

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    val dependencyResolver = new DependencyResolver[c.type, Type, Tree](c, log)(tpe =>
      if (!isWireable(tpe)) c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")
      else c.Expr[T](q"wireRec[$tpe]").tree
    )

    wire(c)(dependencyResolver)
  }

  def wire[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencyResolver: DependencyResolver[c.type, c.universe.Type, c.universe.Tree]): c.Expr[T] = {
    import c.universe._

    val constructorCrimper = new ConstructorCrimper[c.type, T](c, log)
    val companionCrimper = new CompanionCrimper[c.type, T](c, log)

    lazy val targetType = companionCrimper.targetType.toString
    lazy val whatWasWrong: String = {
      if (constructorCrimper.constructor.isEmpty && companionCrimper.companionType.isEmpty)
        s"Cannot find a public constructor nor a companion object for [$targetType]"
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.isEmpty)
        s"Companion object for [$targetType] has no apply methods constructing target type."
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.size > 1)
        s"No public primary constructor found for $targetType and multiple matching apply methods in its companion object were found."
      else s"Target type not supported for wiring: $targetType. Please file a bug report with your use-case."
    }

    val code: Tree = (constructorCrimper.constructorTree(dependencyResolver) orElse companionCrimper.applyTree(
      dependencyResolver
    )) getOrElse
      c.abort(c.enclosingPosition, whatWasWrong)
    log(s"Generated code: ${showCode(code)}, ${showRaw(code)}")
    c.Expr(code)
  }

  def wireWith_impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree): c.Tree = {
    import c.universe._

    val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
    val dependencyResolver = DependencyResolver.throwErrorOnResolutionFailure[c.type, Type, Tree](c, log)
    import typeCheckUtil.typeCheckIfNeeded

    val (params, fun) = factory match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => (p, f)
      case Block(Nil, Function(p, Apply(f, _)))           => (p, f)
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => (p, f)
      case Function(p, Apply(f, _))           => (p, f)
      // Other types not supported
      case _ => c.abort(c.enclosingPosition, s"Not supported factory type: [$factory]")
    }

    val values = params.map { case vd @ ValDef(_, name, tpt, rhs) =>
      dependencyResolver.resolve(vd.symbol, typeCheckIfNeeded(tpt))
    }
    val code = q"$fun(..$values)"

    log("Generated code: " + showCode(code))
    code
  }

  def wireSet_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._
    val targetType = implicitly[c.WeakTypeTag[T]]

    val dependencyResolver = DependencyResolver.throwErrorOnResolutionFailure[c.type, Type, Tree](c, log)

    val instances = dependencyResolver.resolveAll(targetType.tpe)

    // The lack of hygiene can be seen here as a feature, the choice of Set implementation
    // is left to the user - you want a `mutable.Set`, just import `mutable.Set` before the `wireSet[T]` call
    val code = q"Set(..$instances)"

    log("Generated code: " + show(code))
    code
  }

  def wiredInModule_impl(c: blackbox.Context)(in: c.Expr[AnyRef]): c.Tree = {
    import c.universe._

    def extractTypeFromNullaryType(tpe: Type) = {
      tpe match {
        case NullaryMethodType(underlying) => Some(underlying)
        case _                             => None
      }
    }

    val capturedIn = TermName(c.freshName())

    def instanceFactoriesByClassInTree(tree: Tree): List[Tree] = {
      val members = tree.tpe.members

      val pairs = members
        .filter(s => s.isMethod && s.isPublic)
        .flatMap { m =>
          extractTypeFromNullaryType(m.typeSignature) match {
            case Some(tpe) => Some((m, tpe))
            case None =>
              log(s"Cannot extract type from ${m.typeSignature} for member $m!")
              None
          }
        }
        .filter { case (_, tpe) => tpe <:< typeOf[AnyRef] }
        .map { case (member, tpe) =>
          val key = Literal(Constant(tpe))
          val value = q"$capturedIn.$member"

          log(s"Found a mapping: $key -> $value")

          q"scala.Predef.ArrowAssoc($key) -> (() => $value)"
        }

      pairs.toList
    }

    log.withBlock(s"Generating wired-in-module for ${in.tree}") {
      val pairs = instanceFactoriesByClassInTree(in.tree)

      val code =
        q"""
          val $capturedIn = $in
          com.softwaremill.macwire.Wired(scala.collection.immutable.Map(..$pairs))
       """

      log(s"Generated code: " + show(code))
      code
    }
  }

  def autowire_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[T] = {
    import c.universe._

    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

    sealed trait Provider {
      def `type`: Type
    }

    case class Instance(value: Tree) extends Provider {
      lazy val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value)
      lazy val ident: Tree = value
    }

    class FactoryMethod(params: List[ValDef], fun: Tree) extends Provider {

      def result(resolver: Type => Tree): Instance = new Instance(applyWith(resolver))
    
      lazy val `type`: Type = fun.symbol.asMethod.returnType

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

    def providerFromExpr(expr: Expr[Any]): Provider = {
      val tree = expr.tree
      FactoryMethod.fromTree(tree)
        .getOrElse(new Instance(tree))
    }

    val providers = dependencies.map(providerFromExpr)

    log(s"PURE exprs: s[${dependencies.mkString(", ")}]")
    log(s"PURE Providers: [${providers.mkString(", ")}]")
    log(s"PURE ProvidersTYPES: [${providers.map(_.`type`).mkString(", ")}]")

    def findProvider(tpe: Type): Option[Tree] = providers.find(_.`type` <:< tpe).map {
      case i: Instance => i.ident
      case fm: FactoryMethod => fm.result(findProvider(_).getOrElse(c.abort(c.enclosingPosition, "TODO3"))).ident
    }
    
    val code = wireWithResolver(c)(findProvider(_)) getOrElse c.abort(c.enclosingPosition, s"Failed for [$targetType]")//FIXME Improve error tracing
    log(s"Code: [$code]")

    c.Expr[T](code)
  }

  def wireWithResolver[T: c.WeakTypeTag](
      c: blackbox.Context
  )(resolver: c.Type => Option[c.Tree]) = {
    import c.universe._

    def isWireable(tpe: Type): Boolean = {
      val name = tpe.typeSymbol.fullName

      !name.startsWith("java.lang.") && !name.startsWith("scala.")
    }

    lazy val resolutionWithFallback: (Symbol, Type) => Tree = (_, tpe) =>
      if (isWireable(tpe)) resolver(tpe).orElse(go(tpe)).getOrElse(c.abort(c.enclosingPosition, s"TODO???"))
      else c.abort(c.enclosingPosition, s"Cannot find a value of type: [${tpe}]")

    def go(t: Type): Option[Tree] =
      (ConstructorCrimper.constructorTree(c, log)(t, resolutionWithFallback) orElse CompanionCrimper
        .applyTree(c, log)(t, resolutionWithFallback))


    go(implicitly[c.WeakTypeTag[T]].tpe)
  }

}
