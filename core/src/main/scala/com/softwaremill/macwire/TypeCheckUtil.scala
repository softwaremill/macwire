package com.softwaremill.macwire

import scala.reflect.macros.Context

class TypeCheckUtil[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  def typeCheckExpressionOfType(typeTree: Tree): Type = {
    val identityInvWithString = reify { identity[String](null) }
    val Expr(Apply(TypeApply(identityInvFun, _), identityInvArgs)) = identityInvWithString

    val identityInvWithParent = Apply(TypeApply(identityInvFun, List(typeTree)), identityInvArgs)
    val identityInvTypeChecked = c.typeCheck(identityInvWithParent)

    identityInvTypeChecked.tpe
  }

  def candidateTypeOk(tpe: Type) = {
    !(tpe =:= typeOf[Nothing]) && !(tpe =:= typeOf[Null])
  }
}
