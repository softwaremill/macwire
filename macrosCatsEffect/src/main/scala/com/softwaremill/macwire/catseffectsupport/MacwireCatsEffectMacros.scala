package com.softwaremill.macwire
package catseffectsupport

import scala.reflect.macros.blackbox
import cats.effect.{IO, Resource => CatsResource}
import com.softwaremill.macwire.internals._
import java.util.concurrent.atomic.AtomicLong
import cats.effect.kernel
import java.util.concurrent.atomic.AtomicInteger

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
    val counter = new AtomicInteger(0)
      log(s"PROVIDERS: [${providers.mkString(", ")}]")
    def findProvider(t: Type): Option[Provider] = providers.get(t)
    def go(t: Type): Tree ={
      println(s"GO FOR [$t]")
      // val dependencyResolver = new DependencyResolver2[c.type, Type, Tree](c, log)(resources.map(r => (r.resourceType, r.ident)).toMap)(t1 => if (counter.incrementAndGet() < 10) q"wireResourceRec[$t1](..$dependencies)" else c.abort(c.enclosingPosition, "KAPPA"))
      val dependencyResolver = new DependencyResolver2[c.type, Type, Tree](c, log)(resources.map(r => (r.resourceType, r.ident)).toMap)(go(_))
      val companionCrimper = new CompanionCrimperTypeBased[c.type, Type](c, log, t)
      val constructorCrimper = new ConstructorCrimperTypeBased[c.type, Type](c, log, t)
      // val constructorCrimper = new ConstructorCrimper[c.type, T](c, log)
      // val companionCrimper = new CompanionCrimper[c.type, T](c, log)
      val r = (constructorCrimper.constructorTree(dependencyResolver) orElse companionCrimper.applyTree(dependencyResolver)) getOrElse
      c.abort(c.enclosingPosition, "KAPPA")
      
      println(s"CONSTRUCTED [$r]")
      r
    } 
    // findProvider(t) match {
    //   case Some(r: Resource) => r.ident
    //   case Some(fm: FactoryMethod) => {
    //     lazy val paramLists: List[List[Symbol]] = List(fm.params.map(_.symbol))

    //     def wireParams(paramLists: List[List[Symbol]]): List[List[Tree]] =
    //       paramLists.map(_.map(p => go(p.typeSignature)))

    //     val applyArgs: List[List[Tree]] =wireParams(paramLists)

    //     applyArgs.foldLeft(fm.value)((acc: Tree, args: List[Tree]) => Apply(acc, args))
    //   }
    //   case None => c.abort(c.enclosingPosition, s"Cannot provide a value of type: [$t]")
    // }
    
    val generatedInstance = go(targetType.tpe)

    val code = resources.foldRight(q"cats.effect.Resource.pure[cats.effect.IO, $targetType]($generatedInstance)") { case (resource, acc) =>
      
      q"${resource.value}.flatMap((${resource.ident}: ${resource.tpe}) => $acc)"
    }
    log(s"\n\nCODE: [$code]\n\n")
    println(s"\n\nCODE: [$code]\n\n")
    c.Expr[CatsResource[IO, T]](code)

  }
  class DependencyResolver2[C <: blackbox.Context, TypeC <: C#Type, TreeC <: C#Tree](override val c: C, debug: Logger)(values: Map[TypeC, TreeC])(resolutionFallback: TypeC => TreeC) extends DependencyResolver[C, TypeC, TreeC](c, debug)(resolutionFallback) {
     import c.universe._

     override def resolve(param: Symbol, t: Type): Tree = {
       println(s"LOOKING FOR PARAM [$param] TYPE [$t] in SET [${values.keySet.mkString(", ")}]")
      //  values(t.asInstanceOf[TypeC]).asInstanceOf[Tree]
       log(s"LOOKING FOR PARAM [$param] TYPE [$t]")
       values.get(t.asInstanceOf[TypeC]) match {
         case Some(value) =>
          println(s"FOUND [$value] for [$t]") 
          value.asInstanceOf[Tree]
         case None => 
          log(s"FALLBACK TRIGGERED FOR [$param]:[$t]")
          resolutionFallback(t.asInstanceOf[TypeC]).asInstanceOf[Tree]
       }
     }
   }

}
