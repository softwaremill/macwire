package com.softwaremill.macwire

import com.softwaremill.macwire.dependencyLookup._

import scala.reflect.macros.blackbox

object MacwireMacros {
  private val log = new Logger()

  def wire_impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] = {
    import c.universe._

    lazy val dependencyResolver = new DependencyResolver[c.type](c, log)

    def createNewTargetWithParams(): Expr[T] = {
      val targetType = implicitly[c.WeakTypeTag[T]]
      log.withBlock(s"Trying to find parameters to create new instance of: [${targetType.tpe}] at ${c.enclosingPosition}") {
        val targetConstructorOpt = targetType.tpe.members.find(m => m.isMethod && m.asMethod.isPrimaryConstructor)
        targetConstructorOpt match {
          case None =>
            c.abort(c.enclosingPosition, "Cannot find constructor for " + targetType)
          case Some(targetConstructor) =>
            val targetConstructorParamLists = targetConstructor.asMethod.paramLists
            // We need to get the "real" type in case the type parameter is a type alias - then it cannot
            // be directly instatiated
            val targetTpe = targetType.tpe.dealias

            val (sym, tpeArgs) = targetTpe match {
              case TypeRef(_, sym, tpeArgs) => (sym, tpeArgs)
              case t => c.abort(c.enclosingPosition, s"Target type not supported for wiring: $t. Please file a bug report with your use-case.")
            }

            var newT: Tree = Select(New(Ident(targetTpe.typeSymbol)), termNames.CONSTRUCTOR)

            for {
              targetConstructorParams <- targetConstructorParamLists if !targetConstructorParams.headOption.exists(_.isImplicit)
            } {
              val constructorParams: List[c.Tree] = for (param <- targetConstructorParams) yield {
                // Resolve type parameters
                val pTpe = param.typeSignature.substituteTypes(sym.asClass.typeParams, tpeArgs)

                val pEffectiveTpe = if (param.asTerm.isByNameParam) {
                  pTpe.typeArgs.head
                } else {
                  pTpe
                }

                val wireToOpt = dependencyResolver.resolve(param, pEffectiveTpe)

                // If no value is found, an error has been already reported.
                wireToOpt.getOrElse(reify(null).tree)
              }

              newT = Apply(newT, constructorParams)
            }

            log(s"Generated code: ${show(newT)}")
            c.Expr(newT)
        }
      }
    }

    createNewTargetWithParams()
  }

  def wireWith_impl[T: c.WeakTypeTag](c: blackbox.Context)(factory: c.Tree): c.Tree = {
    import c.universe._

    val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
    val dependencyResolver = new DependencyResolver[c.type](c, log)
    import typeCheckUtil.typeCheckIfNeeded

    val Block(Nil, Function(params, Apply(fun, _))) = factory
    val values = params.map {
      case vd @ ValDef(_, name, tpt, rhs) =>
        dependencyResolver.resolve(vd.symbol, typeCheckIfNeeded(tpt)).getOrElse(reify(null).tree)
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

      val code = q"""
          val $capturedIn = $in
          com.softwaremill.macwire.Wired(scala.collection.immutable.Map(..$pairs))
       """

      log(s"Generated code: " + show(code))
      code
    }
  }
}
