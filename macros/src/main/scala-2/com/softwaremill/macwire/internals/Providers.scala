package com.softwaremill.macwire.internals

import scala.reflect.macros.blackbox

class Providers[C <: blackbox.Context](c: C, log: Logger) {
  val con = c //FIXME just to avoid name conflicts in case of import all

  import con.universe._

  lazy val typeCheckUtil = new TypeCheckUtil[con.type](con, log)

  trait Provider {
    def `type`: Type
  }

  trait Value extends Provider {
    def ident: Tree
  }

  case class Instance(value: Tree) extends Value {
    lazy val `type`: Type = typeCheckUtil.typeCheckIfNeeded(value)
    lazy val ident: Tree = value
  }

  class FactoryMethod(params: List[ValDef], fun: Tree) extends Provider {

    def result(resolver: Type => Tree): Value = new Instance(applyWith(resolver))

    def maybeResult(resolver: Type => Option[Tree]): Option[Value] = {

      println(s"BASE METHOD")
      maybeApplyWith(resolver).map { resultTree =>
        
        Instance(resultTree)
      }
    }
      

    lazy val `type`: Type = fun.symbol.asMethod.returnType

    def applyWith(resolver: Type => Tree): Tree = {
      val values = params.map { case ValDef(_, name, tpt, rhs) =>
        resolver(typeCheckUtil.typeCheckIfNeeded(tpt))
      }

      q"$fun(..$values)"
    }

    def maybeApplyWith(resolver: Type => Option[Tree]): Option[Tree] =
      sequence(
      params
        .map { case ValDef(_, name, tpt, rhs) =>
          resolver(typeCheckUtil.typeCheckIfNeeded(tpt))
        }
      )
        .map(values => q"$fun(..$values)")


  }

  object FactoryMethod {
    def fromTree(tree: Tree): Option[FactoryMethod] = tree match {
      // Function with two parameter lists (implicit parameters) (<2.13)
      case Block(Nil, Function(p, Apply(Apply(f, _), _))) => Some(new FactoryMethod(p, f))
      case Block(Nil, Function(p, Apply(f, _)))           => Some(new FactoryMethod(p, f))
      // Function with two parameter lists (implicit parameters) (>=2.13)
      case Function(p, Apply(Apply(f, _), _)) => Some(new FactoryMethod(p, f))
      case Function(p, Apply(f, _))           => Some(new FactoryMethod(p, f))
      // Other types not supported
      case _ => None
    }

  }
}
