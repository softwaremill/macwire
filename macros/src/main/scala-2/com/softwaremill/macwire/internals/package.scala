package com.softwaremill.macwire

import scala.reflect.macros.blackbox

package object internals {
  def isWireable[C <: blackbox.Context](c: C)(tpe: c.Type): Boolean = {
    val name = tpe.typeSymbol.fullName

    !name.startsWith("java.lang.") && !name.startsWith("scala.")
  }

  def paramType[C <: blackbox.Context](c: C)(targetTypeD: c.Type, param: c.Symbol): c.Type = {
    import c.universe._

    val (sym: Symbol, tpeArgs: List[Type]) = targetTypeD match {
      case TypeRef(_, sym, tpeArgs) => (sym, tpeArgs)
      case t =>
        c.abort(
          c.enclosingPosition,
          s"Target type not supported for wiring: $t. Please file a bug report with your use-case."
        )
    }
    val pTpe = param.typeSignature.substituteTypes(sym.asClass.typeParams, tpeArgs)
    if (param.asTerm.isByNameParam) pTpe.typeArgs.head else pTpe
  }
}
