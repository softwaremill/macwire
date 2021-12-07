package com.softwaremill.macwire

import scala.reflect.macros.blackbox

package object internals {
  //FIXME any built-in solution?
  def sequence[A](l: List[Option[A]]) = (Option(List.empty[A]) /: l) {
    case (Some(sofar), Some(value)) => Some(value :: sofar);
    case (_, _)                     => None
  }

  def composeOpts[A, B](f: A => Option[B], fs: A => Option[B]*): A => Option[B] = fs.fold(f) { case (f1, f2) =>
    (a: A) => f1(a).orElse(f2(a))
  }
  def composeWithFallback[A, B](f: A => Option[B], fs: A => Option[B]*)(value: A => B): A => B = (a: A) =>
    composeOpts(f, fs: _*)(a).getOrElse(value(a))

  def combine[A, B](fs: Seq[A => B])(op: B => B => B): A => B = fs.reduce { (f1, f2) => (a: A) => op(f1(a))(f2(a)) }

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
