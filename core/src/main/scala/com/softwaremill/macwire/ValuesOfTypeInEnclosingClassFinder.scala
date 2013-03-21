package com.softwaremill.macwire

import reflect.macros.Context
import annotation.tailrec

private[macwire] class ValuesOfTypeInEnclosingClassFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  def find(t: Type): List[Name] = {
    @tailrec
    def doFind(trees: List[Tree], acc: List[Name]): List[Name] = trees match {
      case Nil => acc
      case tree :: tail => tree match {
        // TODO: subtyping
        case x@ValDef(_, name, tpt, rhs) => {
          doFind(tail, checkCandidate(name, tpt.tpe, rhs, acc, "val"))
        }
        case x@DefDef(_, name, _, _, tpt, rhs) => {
          doFind(tail, checkCandidate(name, tpt.tpe, rhs, acc, "def"))
        }
        case _ => doFind(tail, acc)
      }
    }

    def checkCandidate(name: Name, tpe: Type, rhs: Tree, acc: List[Name], candidateDebugName: String): List[Name] = {
      debug.withBlock(s"Checking $candidateDebugName: [$name]") {
        val rhsTpe = if (tpe != null) {
          tpe
        } else {
          // Disabling macros, no to get into an infinite loop.
          // Duplicating the tree, not to modify the original.
          debug(s"The type is not yet available. Trying a type-check ...")
          val calculatedType = c.typeCheck(rhs.duplicate, silent = true, withMacrosDisabled = true).tpe
          debug(s"Result of type-check: [$calculatedType]")
          calculatedType
        }

        if (rhsTpe == t) {
          debug(s"Found a match in enclosing class/trait!")
          name.encodedName :: acc
        } else {
          acc
        }
      }
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
