package com.softwaremill.macwire

import com.softwaremill.macwire.internals._

import scala.reflect.macros.blackbox

object MacwireMacros {
  private val log = new Logger()

  def wire_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] = {
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

    val code: Tree = (constructorCrimper.constructorTree orElse companionCrimper.applyTree) getOrElse
      c.abort(c.enclosingPosition, whatWasWrong)
    log(s"Generated code: ${showCode(code)}, ${showRaw(code)}")
    c.Expr(code)
  }

  def wireWith_impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree): c.Tree = {
    import c.universe._

    val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
    val dependencyResolver = new DependencyResolver[c.type](c, log)
    import typeCheckUtil.typeCheckIfNeeded

    val (params, fun) = factory match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => (p, f)
      case Block(Nil, Function(p, Apply(f, _))) => (p, f)
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => (p, f)
      case Function(p, Apply(f, _)) => (p, f)
    }

    val values = params.map {
      case vd@ValDef(_, name, tpt, rhs) => dependencyResolver.resolve(vd.symbol, typeCheckIfNeeded(tpt))
    }
    val code = q"$fun(..$values)"

    log("Generated code: " + showCode(code))
    code
  }

  def wireSet_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._
    val targetType = implicitly[c.WeakTypeTag[T]]

    val dependencyResolver = new DependencyResolver[c.type](c, log)

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
        case _ => None
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
}
