package com.softwaremill.macwire.dependencyLookup

import com.softwaremill.macwire.{Debug, TypeCheckUtil}

import scala.annotation.tailrec
import scala.reflect.macros.Context

private[dependencyLookup] class ValuesOfTypeInEnclosingMethodsAndFunctionsFinder[C <: Context](val c: C, debug: Debug) {

  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)

  def find(t: Type, param: Symbol): List[Tree] = {

    def containsCurrentlyExpandedWireCall(t: Tree): Boolean = t.exists(_.pos == c.enclosingPosition)

    @tailrec
    def doFind(trees: List[Tree], acc: List[List[Name]]): List[List[Name]] = trees match {
      case Nil => acc
      case tree :: tail => tree match {
        case Block(statements, expr) => doFind(expr :: statements, acc)
        case ValDef(_, name, _, rhs) if containsCurrentlyExpandedWireCall(rhs) =>
          debug(s"Looking in val $name")
          doFind(List(rhs), acc)
        case DefDef(_, name, _, curriedParams, tpt, rhs) if containsCurrentlyExpandedWireCall(rhs) =>
          debug(s"Looking in def $name")
          doFind(List(rhs), extractMatchingParams(curriedParams.flatten) :: acc)
        case Function(params, body) if containsCurrentlyExpandedWireCall(body) =>
          debug(s"Looking in anonymous function")
          doFind(List(body), extractMatchingParams(params) :: acc)
        case ifBlock @ If(cond, then, otherwise) if containsCurrentlyExpandedWireCall(ifBlock) =>
          doFind(List(then, otherwise), acc)
        case Match(_, cases) if cases.exists(containsCurrentlyExpandedWireCall) =>
          doFind(cases, acc)
        case CaseDef(_, _, body) if containsCurrentlyExpandedWireCall(body) =>
          doFind(List(body), acc)
        case oth =>
          doFind(tail, acc)
      }
    }

    def extractMatchingParams(params: List[ValDef]): List[Name] = params.collect({
      case param@ValDef(_, name, tpt, _) if typeCheckUtil.checkCandidate(t, name, tpt, param, "param") =>
        name
    })

    val enclosingClassBody = c.enclosingClass match {
      case ClassDef(_, _, _, Template(_, _, body)) => body
      case ModuleDef(_, _, Template(_, _, body)) => body
      case e =>
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
    }

    debug.withBlock(s"Looking in the enclosing methods/functions") {
      doFind(enclosingClassBody, Nil).flatten.map(Ident(_))
    }
  }


}
