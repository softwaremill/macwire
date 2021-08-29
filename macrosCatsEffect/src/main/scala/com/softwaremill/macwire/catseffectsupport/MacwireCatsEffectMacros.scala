package com.softwaremill.macwire
package catseffectsupport

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._
import java.util.concurrent.atomic.AtomicLong
import cats.effect.kernel

object MacwireCatsEffectMacros {
  // type ComposableResource[T] = CatsResource[IO, T]
  private val log = new Logger()

  def wireResourceRec_impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(dependencies: c.Expr[Any]*): c.Expr[CatsResource[IO, T]] = {
    import c.universe._
    val targetType = implicitly[c.WeakTypeTag[T]]
    lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)
    val rit: c.WeakTypeTag[CatsResource[IO, T]] = implicitly


    sealed trait Provider //FIXME name....
    case class Resource(value: Tree) extends Provider {
      lazy val resourceType = typeCheckUtil.typeCheckIfNeeded(value).typeArgs(1)
      lazy val ident = Ident(TermName(c.freshName()))
      lazy val tpe = typeCheckUtil.typeCheckIfNeeded(value)
    }

    case class FactoryMethod(value: Tree) extends Provider {
      lazy val returnType: Type = value match {
        case f: Function => f.body.tpe
      }
      lazy val params = value match {
        case f: Function => f.vparams
      }
    }

    val (r, fm) = dependencies.partition(dep =>
      typeCheckUtil
        .typeCheckIfNeeded(dep.tree)
        .typeConstructor =:= rit.tpe.typeConstructor //FIXME there is a better way to check resource type, I believe + we need to check if `factoryMethods` really contains factory methods
    )

    val resources = r.map(expr => Resource(expr.tree))
    val factoryMethods = fm.map(expr => FactoryMethod(expr.tree))

    log(s"RESOURCE: [${resources.mkString(", ")}]")
    log(s"FACTORY_METHODS: [${factoryMethods.mkString(", ")}]")

    val providers: Map[Type, Provider] =
      resources.map(r => (r.resourceType, r)).toMap ++ factoryMethods.map(fm => (fm.returnType, fm)).toMap

      log(s"PROVIDERS: [${providers.mkString(", ")}]")
    def findProvider(t: Type): Option[Provider] = providers.get(t)

    def go(t: Type): Tree = findProvider(t) match {
      case Some(r: Resource) => r.ident
      case Some(fm: FactoryMethod) => {
        lazy val paramLists: List[List[Symbol]] = List(fm.params.map(_.symbol))

        def wireParams(paramLists: List[List[Symbol]]): List[List[Tree]] =
          paramLists.map(_.map(p => go(p.typeSignature)))

        val applyArgs: List[List[Tree]] =wireParams(paramLists)

        applyArgs.foldLeft(fm.value)((acc: Tree, args: List[Tree]) => Apply(acc, args))
      }
      case None => c.abort(c.enclosingPosition, s"Cannot provide a value of type: [$t]")
    }
    
    val generatedInstance = go(targetType.tpe)

    val code = resources.foldRight(q"cats.effect.Resource.pure[cats.effect.IO, $targetType]($generatedInstance)") { case (resource, acc) =>
      
      q"${resource.value}.flatMap((${resource.ident}: ${resource.tpe}) => $acc)"
    }
    log(s"CODE: [$code]")
    c.Expr[CatsResource[IO, T]](code)

  }

}
