package com.softwaremill.macwire

import com.softwaremill.macwire.internals.*
import scala.quoted.*
import java.sql.Types

object MacwireMacros {
  private val log = new Logger()

  def wireImpl[T: Type](using q: Quotes): Expr[T] = {
    import q.reflect.*

    val constructorCrimper = new ConstructorCrimper[q.type, T](log)
    val companionCrimper = new CompanionCrimper[q.type, T](log)
    
    lazy val whatWasWrong: String = {
      if (constructorCrimper.constructor.isEmpty && companionCrimper.applies.isDefined && companionCrimper.applies.get.isEmpty)
        s"Cannot find a public constructor and the companion object has no apply methods constructing target type for [${showTypeName[T]}]"
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.size > 1)
        s"No public primary constructor found for ${showTypeName[T]} and multiple matching apply methods in its companion object were found."
      else s"Target type not supported for wiring: ${showTypeName[T]}. Please file a bug report with your use-case."
    }

    val code: Tree = (constructorCrimper.constructorTree orElse companionCrimper.applyTree)
      .getOrElse(report.throwError(whatWasWrong))
      
    log(s"Generated code: ${code.show}, ${code}")
    code.asExprOf[T]
  }

  def wireWith_impl[T: Type](using q: Quotes)(factory: Expr[Any]): Expr[T] = {
    import q.reflect.*

    val typeCheckUtil = new TypeCheckUtil[q.type](log)
    val dependencyResolver = new DependencyResolver[q.type, T](log)
    // import typeCheckUtil.typeCheckIfNeeded

    println(s"FACTORY [${factory.asTerm}]")
    
    val (params, fun) = factory.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, List(p), _, Some(Apply(f, _)))),_)) => (p.params, f)
      case _ => report.throwError(s"Not supported factory type: [$factory]")
      
    }

    val values = params.map {
      // case vd@ValDef(_, name, tpt, rhs) => dependencyResolver.resolve(vd.symbol, typeCheckIfNeeded(tpt))
      case vd@ValDef(name, tpt, rhs) => dependencyResolver.resolve(vd.symbol, tpt.tpe)
    }

    println(s"Values [${values.mkString(", ")}]")
    
    val code = Apply(fun, values).asExprOf[T]

    code
  }

  def wireSet_impl[T: Type](using q: Quotes): Expr[Set[T]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T]
    val dependencyResolver = new DependencyResolver[q.type, T](log)

    val instances = dependencyResolver.resolveAll(tpe)

    // The lack of hygiene can be seen here as a feature, the choice of Set implementation
    // is left to the user - you want a `mutable.Set`, just import `mutable.Set` before the `wireSet[T]` call
    val code = '{ ${ Expr.ofSeq(instances.toSeq.map(_.asExprOf[T])) }.toSet }
    

    log(s"Generated code: ${code.show}")
    code
  }

}
