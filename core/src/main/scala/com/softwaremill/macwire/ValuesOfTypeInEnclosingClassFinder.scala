package com.softwaremill.macwire

import reflect.macros.Context
import annotation.tailrec

private[macwire] class ValuesOfTypeInEnclosingClassFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)

  def find(t: Type): List[Name] = {
    @tailrec
    def doFind(trees: List[Tree], acc: List[Name]): List[Name] = trees match {
      case Nil => acc
      case tree :: tail => tree match {
        case ValDef(_, name, tpt, rhs) => {
          doFind(tail, checkCandidate(name, tpt, treeToCheck(tree, rhs), acc, "val"))
        }
        case DefDef(_, name, _, _, tpt, rhs) => {
          doFind(tail, checkCandidate(name, tpt, treeToCheck(tree, rhs), acc, "def"))
        }
        case _ => doFind(tail, acc)
      }
    }

    def checkCandidate(name: Name, tpt: Tree, treeToCheck: Tree, acc: List[Name], candidateDebugName: String): List[Name] = {
      debug.withBlock(s"Checking $candidateDebugName: [$name]") {
        val rhsTpe = if (tpt.tpe != null) {
          tpt.tpe
        } else {
          // Disabling macros, no to get into an infinite loop.
          // Duplicating the tree, not to modify the original.
          debug(s"The type is not yet available. Trying a type-check ...")
          val calculatedType = c.typeCheck(treeToCheck.duplicate, silent = true, withMacrosDisabled = true).tpe
          // In case of abstract definitions, when we check the tree (not the rhs), the result is in tpt.tpe. Otherwise,
          // it's in calculatedType.
          val result = if (tpt.tpe == null) calculatedType else tpt.tpe

          if (result == NoType) {
            debug("Type check didn't resolve to a type. Trying to type-check an expression of the given type.")
            val result2 = typeCheckUtil.typeCheckExpressionOfType(tpt)

            debug(s"Result of second type-check: [$result2]")

            result2
          } else {
            debug(s"Result of type-check: [$result]")
            result
          }
        }

        if (rhsTpe <:< t && typeCheckUtil.candidateTypeOk(rhsTpe)) {
          debug(s"Found a match in enclosing class/trait!")
          name.encodedName :: acc
        } else {
          acc
        }
      }
    }

    def treeToCheck(tree: Tree, rhs: Tree) = {
      // If possible, we check the definition (rhs). We can't always check the tree, as it would cause recursive
      // type ascription needed errors from the compiler.
      if (rhs.isEmpty) tree else rhs
    }

    val enclosingClassBody = c.enclosingClass match {
      case ClassDef(_, _, _, Template(_, _, body)) => body
      case ModuleDef(_, _, Template(_, _, body)) => body
      case e => {
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
      }
    }

    debug("Looking in the enclosing class/trait")
    doFind(enclosingClassBody, Nil)
  }
}
