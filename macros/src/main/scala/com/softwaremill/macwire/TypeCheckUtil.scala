package com.softwaremill.macwire

import scala.reflect.macros.blackbox

private[macwire] class TypeCheckUtil[C <: blackbox.Context](val c: C, debug: Debug) {
  import c.universe._

  def typeCheckIfNeeded(tree: Tree): Type = {
    if( tree.tpe != null ) {
      tree.tpe
    } else {
//      debug(s"Trying to type-check $tree") // this log is almost always present => need trace level
      val tpe = typeCheckExpressionOfType(tree)
      debug(s"Type-checking found $tpe")
      tpe
    }
  }

  /** @param tree the tree that initializes the symbol referenced in `expr` */
  def typeCheckIfNeeded(expr: Tree, tree: Tree): Type = {
    if( expr.tpe != null ) {
      expr.tpe
    } else {
//       debug(s"Trying to type-check $tree") // this log is almost always present => need trace level

      // Disabling macros, to avoid an infinite loop.
      // Duplicating the tree, to not modify the original.
//       debug(s"The type is not yet available. Trying a type-check ...") // this log is almost always present => need trace level
      val calculatedType = c.typecheck(tree.duplicate, silent = true, withMacrosDisabled = true).tpe
      // In case of abstract definitions, when we check the tree (not the rhs), the result is in expr.tpe. Otherwise,
      // it's in calculatedType.
      val result = if (expr.tpe == null) calculatedType else expr.tpe

      if (result == NoType) {
        val tpe = typeCheckExpressionOfType(expr)
        debug(s"Type-checking found $tpe for [$expr]")
        tpe
      } else {
        result
      }
    }
  }

  def checkCandidate(target: Type, tpt: Type): Boolean = {
    val typesToCheck = tpt :: (tpt match {
      case NullaryMethodType(resultType) => List(resultType)
      case MethodType(_, resultType) => List(resultType)
      case _ => Nil
    })

    typesToCheck.exists(ty => ty <:< target && isNotNullOrNothing(ty))
  }

  private def isNotNullOrNothing(tpe: Type): Boolean = {
    !(tpe =:= typeOf[Nothing]) && !(tpe =:= typeOf[Null])
  }

  private def typeCheckExpressionOfType(typeTree: Tree): Type = {
    c.typecheck(q"$typeTree", c.TYPEmode).tpe
  }
}
