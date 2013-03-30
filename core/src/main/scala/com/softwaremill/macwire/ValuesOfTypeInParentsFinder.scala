package com.softwaremill.macwire

import reflect.macros.Context
import annotation.tailrec

private[macwire] class ValuesOfTypeInParentsFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  def find(t: Type): List[Name] = {
    def checkCandidate(tpt: Type): Boolean = {
      val typesToCheck = tpt :: (tpt match {
        case NullaryMethodType(resultType) => {
          List(resultType)
        }
        case MethodType(_, resultType) => {
          List(resultType)
        }
        case _ => Nil
      })

      typesToCheck.exists(_ <:< t)
    }

    def findInParent(parent: Tree): Set[Name] = {
      debug.withBlock(s"Checking parent: [${parent.tpe}]") {
        val result = parent.tpe.members
          .filter(symbol => checkCandidate(symbol.typeSignature))
          .map(_.name)
          // For (lazy) vals, the names have a space at the end of the name (probably some compiler internals).
          // Hence the trim.
          .map(name => newTermName(name.decoded.trim()))

        if (result.size > 0) {
          debug(s"Found ${result.size} matching name(s): [${result.mkString(", ")}]")
        }

        result.toSet
      }
    }

    @tailrec
    def findInParents(parents: List[Tree], acc: Set[Name]): Set[Name] = {
      parents match {
        case Nil => acc
        case parent :: tail => findInParents(tail, findInParent(parent) ++ acc)
      }
    }

    val parents = c.enclosingClass match {
      case ClassDef(_, _, _, Template(parents, _, _)) => parents
      case ModuleDef(_, _, Template(parents, _, _)) => parents
      case e => {
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
      }
    }

    debug("Looking in parents")
    findInParents(parents, Set()).toList
  }
}
