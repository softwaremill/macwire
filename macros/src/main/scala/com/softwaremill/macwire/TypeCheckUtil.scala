package com.softwaremill.macwire

import scala.reflect.macros.blackbox.Context

class TypeCheckUtil[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  def typeCheckExpressionOfType(typeTree: Tree): Type = {
    val someValueOfTypeString = reify {
      def x[T](): T = throw new Exception
      x[String]()
    }

    val Expr(Block(stats, Apply(TypeApply(someValueFun, _), someTypeArgs))) = someValueOfTypeString

    val someValueOfGivenType = Block(stats, Apply(TypeApply(someValueFun, List(typeTree)), someTypeArgs))
    val someValueOfGivenTypeChecked = c.typecheck(someValueOfGivenType)

    someValueOfGivenTypeChecked.tpe
  }

  def candidateTypeOk(tpe: Type) = {
    !(tpe =:= typeOf[Nothing]) && !(tpe =:= typeOf[Null])
  }

  def checkCandidate(target: Type, name: Name, tpt: Tree, treeToCheck: Tree, candidateDebugName: String): Boolean = {
    debug.withBlock(s"Checking $candidateDebugName: [$name]") {
      val rhsTpe = if (tpt.tpe != null) {
        tpt.tpe
      } else {
        // Disabling macros, no to get into an infinite loop.
        // Duplicating the tree, not to modify the original.
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

      val candidateOk = rhsTpe <:< target && candidateTypeOk(rhsTpe)
      if (candidateOk) debug("Found a match!")
      candidateOk
    }
  }
}
