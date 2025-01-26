package com.softwaremill.macwire.internals

import scala.quoted.*
import scala.annotation.Annotation

private[macwire] class ConstructorCrimper[Q <: Quotes, T: Type](using val q: Q)(
    dependencyResolver: DependencyResolver[q.type, T],
    log: Logger
) {
  import q.reflect.*

  lazy val targetType = TypeRepr.of[T]

  // We need to get the "real" type in case the type parameter is a type alias - then it cannot
  // be directly instantiated
  lazy val targetTypeD = targetType.dealias

  private def isAccessibleConstructor(s: Symbol) =
    s.isClassConstructor && !(s.flags is Flags.Private) && !(s.flags is Flags.Protected)

  private def isImplicit(f: Flags): Boolean = f.is(Flags.Implicit) || f.is(Flags.Given)

  lazy val publicConstructors: Iterable[Symbol] = {
    val ctors = targetType.typeSymbol.declarations
      .filter(isAccessibleConstructor)
      .filterNot(isPhantomConstructor)
    log.withBlock(s"There are ${ctors.size} eligible constructors") { ctors.foreach(c => log(showConstructor(c))) }
    ctors
  }

  lazy val primaryConstructor: Option[Symbol] = targetType.typeSymbol.primaryConstructor match {
    case c if isAccessibleConstructor(c) => Some(c)
    case c                               => None
  }

  lazy val injectConstructors: Iterable[Symbol] = {
    val isInjectAnnotation = (a: Term) =>
      a.tpe.typeSymbol.fullName == "javax.inject.Inject" || a.tpe.typeSymbol.fullName == "jakarta.inject.Inject"
    val ctors = publicConstructors.filter(_.annotations.exists(isInjectAnnotation))
    log.withBlock(
      s"There are ${ctors.size} constructors annotated with @javax.inject.Inject or @jakarta.inject.Inject"
    ) {
      ctors.foreach(c => log(showConstructor(c)))
    }
    ctors
  }

  lazy val injectConstructor: Option[Symbol] =
    if (injectConstructors.size > 1)
      abort(
        s"Ambiguous constructors annotated with @javax.inject.Inject or @jakarta.inject.Inject for type [${targetType.typeSymbol.name}]"
      )
    else injectConstructors.headOption

  lazy val constructor: Option[Symbol] = log.withBlock(s"Looking for constructor for $targetType") {
    val ctor = injectConstructor orElse primaryConstructor
    ctor.foreach(ctor => log(s"Found ${showConstructor(ctor)}"))
    ctor
  }

  private def constructorParamTypes(ctorType: TypeRepr): List[List[TypeRepr]] = {
    ctorType match {
      case MethodType(_, paramTypes, retType) =>
        paramTypes.map(_.widen.simplified) :: constructorParamTypes(retType.simplified)
      case _ =>
        Nil
    }
  }

  lazy val constructorParamLists: Option[List[List[(Symbol, TypeRepr)]]] = {
    constructor.map { c =>
      // paramSymss contains both type arg symbols (generic types) and value arg symbols
      val symLists = c.paramSymss.filter(_.forall(!_.isTypeDef))
      val ctorType =
        if (targetType.typeArgs.isEmpty) targetType.memberType(c)
        else targetType.memberType(c).appliedTo(targetType.typeArgs)
      val typeLists = constructorParamTypes(ctorType)
      symLists.zip(typeLists).map { case (syms, tpes) =>
        syms.zip(tpes)
      }
    }
  }

  lazy val constructorArgs: Option[List[List[Term]]] = log.withBlock("Looking for targetConstructor arguments") {
    constructorParamLists.map(wireConstructorParamsWithImplicitLookups)
  }

  lazy val constructorTree: Option[Tree] = log.withBlock(s"Creating Constructor Tree for $targetType") {
    for {
      constructorValue <- constructor
      constructorArgsValue <- constructorArgs
    } yield {
      val constructionMethodTree: Term = {
        val ctor = Select(New(TypeIdent(targetType.typeSymbol)), constructorValue)
        if (targetType.typeArgs.isEmpty) ctor else ctor.appliedToTypes(targetType.typeArgs)
      }
      constructorArgsValue.foldLeft(constructionMethodTree)((acc: Term, args: List[Term]) => Apply(acc, args))
    }
  }

  def wireConstructorParams(paramLists: List[List[(Symbol, TypeRepr)]]): List[List[Term]] =
    paramLists.map(_.map(p => dependencyResolver.resolve(p._1, /*SI-4751*/ p._2)))

  def wireConstructorParamsWithImplicitLookups(paramLists: List[List[(Symbol, TypeRepr)]]): List[List[Term]] =
    paramLists.map {
      case params if params.forall(p => isImplicit(p._1.flags)) => params.map(resolveImplicitOrFail)
      case params => params.map(p => dependencyResolver.resolve(p._1, /*SI-4751*/ p._2))
    }

  private def resolveImplicitOrFail(param: Symbol, paramType: TypeRepr): Term =
    Implicits.search(paramType) match {
      case iss: ImplicitSearchSuccess => iss.tree
      case isf: ImplicitSearchFailure => report.throwError(s"Failed to resolve an implicit for [$param].")
    }

  /** In some cases there is one extra (phantom) constructor. This happens when extended trait has implicit param:
    *
    * {{{
    *   trait A { implicit val a = ??? };
    *   class X extends A
    *   import scala.reflect.runtime.universe._
    *   typeOf[X].members.filter(m => m.isMethod && m.asMethod.isConstructor && m.asMethod.isPrimaryConstructor).map(_.asMethod.fullName)
    *
    *   //res1: Iterable[String] = List(X.<init>, A.$init$)
    * }}}
    *
    * The {{{A.$init$}}} is the phantom constructor and we don't want it.
    *
    * In other words, if we don't filter such constructor using this function 'wireActor-12-noPublicConstructor.failure'
    * will compile and throw exception during runtime but we want to fail it during compilation time.
    */
  // def isPhantomConstructor(constructor: Symbol): Boolean = constructor.asMethod.fullName.endsWith("$init$")
  def isPhantomConstructor(constructor: Symbol): Boolean = constructor.fullName.endsWith("$init$")

  // def showConstructor(c: Symbol): String = c.asMethod.typeSignature.toString
  def showConstructor(c: Symbol): String = c.toString

  def abort(msg: String): Nothing = report.throwError(msg)
}
