package com.softwaremill.macwire

import com.softwaremill.macwire.dependencyLookup._

import reflect.macros.Context
import scala.language.experimental.macros

trait Macwire {
  def wire[T]: T = macro MacwireMacros.wire_impl[T]
  def wiredInModule(in: AnyRef): Wired = macro MacwireMacros.wiredInModule_impl
}

object MacwireMacros extends Macwire {
  private val debug = new Debug()

  def wire_impl[T: c.WeakTypeTag](c: Context): c.Expr[T] = {
    import c.universe._

    lazy val dependencyResolver = new DependencyResolver[c.type](c, debug)

    def createNewTargetWithParams(): Expr[T] = {
      val targetType = implicitly[c.WeakTypeTag[T]]
      debug.withBlock(s"Trying to find parameters to create new instance of: [${targetType.tpe}]") {
        val targetConstructorOpt = targetType.tpe.members.find(_.name.decodedName.toString == "<init>")
        targetConstructorOpt match {
          case None =>
            c.error(c.enclosingPosition, "Cannot find constructor for " + targetType)
            reify { null.asInstanceOf[T] }
          case Some(targetConstructor) =>
            val targetConstructorParamLists = targetConstructor.asMethod.paramss
            val TypeRef(_, sym, tpeArgs) = targetType.tpe
            var newT: Tree = Select(New(Ident(targetType.tpe.typeSymbol)), nme.CONSTRUCTOR)

            for {
              targetConstructorParams <- targetConstructorParamLists
            } {
              val constructorParams: List[c.Tree] = for (param <- targetConstructorParams) yield {
                // Resolve type parameters
                val pTpe = param.typeSignature.substituteTypes(sym.asClass.typeParams, tpeArgs)
                val wireToOpt = dependencyResolver.resolve(param, pTpe)

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
        .filter(_.isMethod)
        .flatMap { m =>
        extractTypeFromNullaryType(m.typeSignature) match {
          case Some(tpe) => Some((m, tpe))
          case None =>
            debug(s"Cannot extract type from ${m.typeSignature} for member $m!")
            None
        }
      }.map { case (member, tpe) =>
        val key = Literal(Constant(tpe))
        val value = Select(Ident(capturedInTermName), newTermName(member.name.decoded.trim))

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
