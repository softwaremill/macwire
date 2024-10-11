package com.softwaremill.macwire.internals

import com.softwaremill.macwire.internals.*
import scala.quoted.*

object MacwireMacros {
  private val log = new Logger()

  def wireImpl[T: Type](using q: Quotes): Expr[T] = {
    val dependencyResolver = DependencyResolver.throwErrorOnResolutionFailure[q.type, T](log)

    wire[T](using q)(dependencyResolver)
  }

  // TODO build failure path
  def wireRecImpl[T: Type](using q: Quotes): Expr[T] = {
    import q.reflect.*

    // FIXME for some reason `TypeRepr.of[String].typeSymbol.owner` and `defn.JavaLangPackage` have different hash codes
    def isWireable(tpe: TypeRepr): Boolean =
      tpe.classSymbol.map(_.owner.fullName != defn.JavaLangPackage.fullName).getOrElse(false)

    val dependencyResolver = new DependencyResolver[q.type, T](using q)(
      log,
      tpe =>
        if !isWireable(tpe) then report.errorAndAbort(s"Cannot find a value of type: [${showTypeName(tpe)}]")
        else
          tpe.asType match {
            case '[t] => wireRecImpl[t].asTerm
          }
    )

    wire[T](using q)(dependencyResolver)
  }

  private def wire[T: Type](using q: Quotes)(dependencyResolver: DependencyResolver[q.type, T]): Expr[T] = {
    import q.reflect.*

    val constructorCrimper = new ConstructorCrimper[q.type, T](using q)(dependencyResolver, log)
    val companionCrimper = new CompanionCrimper[q.type, T](using q)(dependencyResolver, log)

    lazy val whatWasWrong: String = {
      if (
        constructorCrimper.constructor.isEmpty && companionCrimper.applies.isDefined && companionCrimper.applies.get.isEmpty
      )
        s"Cannot find a public constructor and the companion object has no apply methods constructing target type for [${showTypeName[T]}]"
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.size > 1)
        s"No public primary constructor found for ${showTypeName[T]} and multiple matching apply methods in its companion object were found."
      else s"Target type not supported for wiring: ${showTypeName[T]}. Please file a bug report with your use-case."
    }

    val code: Tree = (constructorCrimper.constructorTree orElse companionCrimper.applyTree)
      .getOrElse(report.errorAndAbort(whatWasWrong))

    log(s"Generated code: ${code.show}, ${code}")
    code.asExprOf[T]
  }

  def wireWith_impl[T: Type](using q: Quotes)(factory: Expr[Any]): Expr[T] = {
    import q.reflect.*

    val dependencyResolver = DependencyResolver.throwErrorOnResolutionFailure[q.type, T](log)

    def functionParamTypes(t: TypeRepr): List[List[TypeRepr]] = {
      if (t.isFunctionType) {
        // Handle curried function
        t.typeArgs.init :: functionParamTypes(t.typeArgs.last)
      } else {
        Nil
      }
    }
    // Implicit params are pre-applied while passing to wireWith
    val values = functionParamTypes(factory.asTerm.tpe).zipWithIndex.map { case (paramList, i) =>
      paramList.zipWithIndex.map { case (tpe, j) =>
        // Resolve require a symbol, create a fake symbol here
        val fakeSymbol = Symbol.newVal(Symbol.noSymbol, s"p_${i}_${j}", tpe, Flags.Param, Symbol.noSymbol)
        dependencyResolver.resolve(fakeSymbol, tpe)
      }
    }
    val funApply: Term = Select.unique(factory.asTerm, "apply")
    val code = values
      .foldLeft(funApply) { (fun, args) =>
        Apply(fun, args)
      }
      .asExprOf[T]
    log(s"Generated code: ${code.show}")
    code
  }

  def wireSet_impl[T: Type](using q: Quotes): Expr[Set[T]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T]
    val dependencyResolver = DependencyResolver.throwErrorOnResolutionFailure[q.type, T](log)

    val instances = dependencyResolver.resolveAll(tpe)

    val code = '{ ${ Expr.ofSeq(instances.toSeq.map(_.asExprOf[T])) }.toSet }

    log(s"Generated code: ${code.show}")
    code
  }

}
