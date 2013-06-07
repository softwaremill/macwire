package com.softwaremill.macwire

import reflect.macros.Context
import annotation.tailrec

private[macwire] class ValuesOfTypeInEnclosingMethodFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)

  def find(n: Name, t: Type): List[Name] = {
    @tailrec
    def doFind(params: List[ValDef], acc: List[Name]): List[Name] = params match {
      case Nil => acc
      case (param@ValDef(_, name, tpt, _)) :: tail =>
        val candidateOk = typeCheckUtil.checkCandidate(t, name, tpt, param, "param")
        doFind(tail, if (candidateOk) name :: acc else acc)
    }

    def ifNotUniqueTryByName(names: List[Name]): List[Name] = {
      if (names.size > 1) {
        names.find(_ == n) match {
          case Some(nn) => List(nn)
          case None => names
        }
      } else names
    }

    val methodParams = c.enclosingMethod match {
      case null => Nil
      case DefDef(_, _, _, vparamss, _, _) => vparamss.flatten
      case e => {
        c.error(c.enclosingPosition, s"Unknown tree for enclosing method: ${e.getClass}")
        Nil
      }
    }

    debug("Looking in the enclosing method")
    val candidateNames = doFind(methodParams, Nil)
    ifNotUniqueTryByName(candidateNames)
  }
}
