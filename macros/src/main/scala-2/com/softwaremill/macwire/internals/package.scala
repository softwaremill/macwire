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
    resolveCallByNameParamType(c)(pTpe)
  }

  def resolveCallByNameParamType[C <: blackbox.Context](c: C)(tpe: c.Type): c.Type = {
    import c.universe._

    val cl = c.universe.definitions.ByNameParamClass
    val isByName = tpe match {
      case TypeRef(_, `cl`, _) => true
      case _                   => false
    }

    if (isByName) tpe.typeArgs.head else tpe
  }

}
