package com.softwaremill.macwire

import scala.reflect.macros.blackbox

private[macwire] class TypeCheckUtil[C <: blackbox.Context](val c: C, debug: Debug) {
  import c.universe._

  def typeCheckExpressionOfType(typeTree: Tree): Type = {
    c.typecheck(q"$typeTree", c.TYPEmode).tpe
  }
  
  def typeCheckIfNeeded(tree: Tree): Type = {
    if( tree.tpe != null ) {
      tree.tpe
    } else {
      debug(s"Trying to type-check $tree")
      val tpe = typeCheckExpressionOfType(tree)
      debug(s"Type-checking found $tpe")
      tpe
    }
  }

  def isNotNullOrNothing(tpe: Type): Boolean = {
    !(tpe =:= typeOf[Nothing]) && !(tpe =:= typeOf[Null])
  }

  def checkCandidate(target: Type, tpt: Type): Boolean = {
    val typesToCheck = tpt :: (tpt match {
      case NullaryMethodType(resultType) => List(resultType)
      case MethodType(_, resultType) => List(resultType)
      case _ => Nil
    })

    typesToCheck.exists(ty => ty <:< target && isNotNullOrNothing(ty))
  }

  def checkCandidate(target: Type, name: TermName, tpt: Tree, treeToCheck: Tree, candidateDebugName: String): Boolean = {
    debug.withBlock(s"Checking $candidateDebugName: [$name]") {
      val rhsTpe = if (tpt.tpe != null) {
        tpt.tpe
      } else {
        // Disabling macros, to avoid an infinite loop.
        // Duplicating the tree, to not modify the original.
        debug(s"The type is not yet available. Trying a type-check ...")
        val calculatedType = c.typecheck(treeToCheck.duplicate, silent = true, withMacrosDisabled = true).tpe
        // In case of abstract definitions, when we check the tree (not the rhs), the result is in tpt.tpe. Otherwise,
        // it's in calculatedType.
        val result = if (tpt.tpe == null) calculatedType else tpt.tpe

        if (result == NoType) {
          debug("Type check didn't resolve to a type. Trying to type-check an expression of the given type.")
          val result2 = typeCheckExpressionOfType(tpt)

          debug(s"Result of second type-check: [$result2]")

          result2
        } else {
          debug(s"Result of type-check: [$result]")
          result
        }
      }

      val candidateOk = rhsTpe <:< target && isNotNullOrNothing(rhsTpe)
      if (candidateOk) debug("Found a match!")
      candidateOk
    }
  }
}
