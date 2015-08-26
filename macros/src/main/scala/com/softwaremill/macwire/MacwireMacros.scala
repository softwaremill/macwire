package com.softwaremill.macwire

import com.softwaremill.macwire.dependencyLookup._

import reflect.macros.Context
import scala.language.experimental.macros

trait Macwire {
  def wire[T]: T = macro MacwireMacros.wire_impl[T]
  def wireImplicit[T]: T = macro MacwireMacros.wireImplicit_impl[T]
  def wiredInModule(in: AnyRef): Wired = macro MacwireMacros.wiredInModule_impl
}

object MacwireMacros extends Macwire {
  private val debug = new Debug()

  def wire_impl[T: c.WeakTypeTag](c: Context): c.Expr[T] = doWire(c, wireWithImplicits = false)

  def wireImplicit_impl[T: c.WeakTypeTag](c: Context): c.Expr[T] = doWire(c, wireWithImplicits = true)

  private def doWire[T: c.WeakTypeTag](c: Context, wireWithImplicits: Boolean): c.Expr[T] = {
    import c.universe._

    lazy val dependencyResolver = new DependencyResolver[c.type](c, debug, wireWithImplicits)

    def createNewTargetWithParams(): Expr[T] = {
      val targetType = implicitly[c.WeakTypeTag[T]]
      debug.withBlock(s"Trying to find parameters to create new instance of: [${targetType.tpe}]") {
        val targetConstructorOpt = targetType.tpe.members.find(m => m.isMethod && m.asMethod.isPrimaryConstructor)
        targetConstructorOpt match {
          case None =>
            c.abort(c.enclosingPosition, "Cannot find constructor for " + targetType)
          case Some(targetConstructor) =>
/*
            val targetConstructorParamLists = targetConstructor.asMethod.paramss
            val TypeRef(_, sym, tpeArgs) = targetType.tpe
            var newT: Tree = Select(New(Ident(targetType.tpe.typeSymbol)), nme.CONSTRUCTOR)
*/
            val targetConstructorParamLists = targetConstructor.asMethod.paramss
            // We need to get the "real" type in case the type parameter is a type alias - then it cannot
            // be directly instatiated
            val targetTpe = targetType.tpe // TODO dealias

            val (sym, tpeArgs) = targetTpe match {
              case TypeRef(_, sym, tpeArgs) => (sym, tpeArgs)
              case t => c.abort(c.enclosingPosition, s"Target type not supported for wiring: $t. Please file a bug report with your use-case.")
            }

            var newT: Tree = Select(New(Ident(targetTpe.typeSymbol)), nme.CONSTRUCTOR)

            for {
              targetConstructorParams <- targetConstructorParamLists
            } {
              val constructorParams: List[c.Tree] = for (param <- targetConstructorParams) yield {
                // Resolve type parameters
                val pTpe = param.typeSignature.substituteTypes(sym.asClass.typeParams, tpeArgs)

                val pEffectiveTpe = if (param.asTerm.isByNameParam) {
                  pTpe // TODO.typeArgs.head
                } else {
                  pTpe
                }

                val wireToOpt = dependencyResolver.resolve(param, pEffectiveTpe)

                // If no value is found, an error has been already reported.
                wireToOpt.getOrElse(reify(null).tree)
              }

              newT = Apply(newT, constructorParams)
            }

            debug(s"Generated code: ${c.universe.show(newT)}")
            c.Expr(newT)
        }
      }
    }

    createNewTargetWithParams()
  }

  def wiredInModule_impl(c: Context)(in: c.Expr[AnyRef]): c.Expr[Wired] = {
    import c.universe._

    // Ident(scala.Predef)
    val Expr(predefIdent) = reify { Predef }
    val Expr(wiredIdent) = reify { Wired }

    def extractTypeFromNullaryType(tpe: Type) = {
      tpe match {
        case NullaryMethodType(underlying) => Some(underlying)
        case _ => None
      }
    }

    val capturedInName = c.fresh("capturedIn")
    val capturedInTermName = newTermName(capturedInName)

    def instanceFactoriesByClassInTree(tree: Tree): List[Tree] = {
      val members = tree.tpe.members

      val pairs = members
        .filter(s => s.isMethod && s.isPublic)
        .flatMap { m =>
          extractTypeFromNullaryType(m.typeSignature) match {
            case Some(tpe) => Some((m, tpe))
            case None =>
              debug(s"Cannot extract type from ${m.typeSignature} for member $m!")
              None
          }
        }
        .filter { case (_, tpe) => tpe <:< typeOf[AnyRef] }
        .map { case (member, tpe) =>
          val key = Literal(Constant(tpe))
          val value = Select(Ident(newTermName(capturedInName)), newTermName(member.name.decoded.trim))

          debug(s"Found a mapping: $key -> $value")

          // Generating: () => value
          val valueExpr = c.Expr[AnyRef](value)
          val createValueExpr = reify { () => valueExpr.splice }

        // Generating: key -> value
        Apply(Select(Apply(Select(predefIdent, newTermName("any2ArrowAssoc")), List(key)),
          newTermName("$minus$greater")), List(createValueExpr.tree))
      }

      pairs.toList
    }

    debug.withBlock(s"Generating wired-in-module for ${in.tree}") {
      val pairs = instanceFactoriesByClassInTree(in.tree)

      // Generating:
      // {
      //   val inName = in
      //   Wired(Map(...))
      // }
      val captureInTree = ValDef(Modifiers(), capturedInTermName, TypeTree(in.actualType), in.tree)
      val newWiredTree = Apply(Select(wiredIdent, newTermName("apply")), List(
        Apply(Select(Select(predefIdent, newTermName("Map")), newTermName("apply")), pairs)))
      c.Expr[Wired](Block(List(captureInTree), newWiredTree))
    }
  }
}
