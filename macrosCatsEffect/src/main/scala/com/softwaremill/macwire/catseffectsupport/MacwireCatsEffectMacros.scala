package com.softwaremill.macwire
package catseffectsupport

import scala.reflect.macros.blackbox
import cats.effect._
import com.softwaremill.macwire.internals._
import java.util.concurrent.atomic.AtomicLong

object MacwireCatsEffectMacros {
    private val log = new Logger()

  def wireResourceRec_impl[T: c.WeakTypeTag, A: c.WeakTypeTag](c: blackbox.Context)(resources: c.Expr[Resource[IO, Any]]*): c.Expr[A] = {
    import c.universe._
    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

    val rr = resources.map { er => 
      val name = Ident(TermName(c.freshName()))
      val resourceType = typeCheckUtil.typeCheckIfNeeded(er.tree).typeArgs(1)//FIXME
      (er, name, resourceType)
    }
    
    val code = rr.foldRight(MacwireMacros.wire[T](c)(new DependencyResolver2[c.type, Type, Tree](c, log)(rr.map(t => (t._3, t._2)).toMap)).tree) { case ((er, name, tpe), acc) => 
      println(s"FRESH NAME [$name]")
      q"$er.map(($name: $tpe) => $acc)"
    }
    println(s"CODE: [$code]")
    c.Expr[A](code)
  }
//FIXME 
  class DependencyResolver2[C <: blackbox.Context, TypeC <: C#Type, TreeC <: C#Tree](override val c: C, debug: Logger)(values: Map[TypeC, TreeC]) extends DependencyResolver[C, TypeC, TreeC](c, debug)(t => c.abort(c.enclosingPosition, s"Cannot find a value of type: [$t]")) {
    import c.universe._

    override def resolve(param: Symbol, t: Type): Tree = {
      println(s"LOOKING FOR PARAM [$param] TYPE [$t]")
      values(t.asInstanceOf[TypeC]).asInstanceOf[Tree]
    }
  }
}
