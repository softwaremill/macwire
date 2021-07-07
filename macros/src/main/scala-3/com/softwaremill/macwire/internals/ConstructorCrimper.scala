package com.softwaremill.macwire.internals

import scala.quoted.*
import scala.annotation.Annotation

private[macwire] class ConstructorCrimper[Q <: Quotes, T: Type](using val q: Q)(dependencyResolver: => DependencyResolver[q.type, T], log: Logger) {
  import q.reflect.*

  // lazy val typeCheckUtil = new TypeCheckUtil[c.type](c, log)

  lazy val targetType = TypeRepr.of[T]

  // We need to get the "real" type in case the type parameter is a type alias - then it cannot
  // be directly instantiated
  lazy val targetTypeD = targetType.dealias

  // lazy val classOfT: Expr[Class[T]] = c.Expr[Class[T]](q"classOf[$targetType]")

  private def isAccessibleConstructor(s: Symbol) = s.isClassConstructor && !(s.flags is Flags.Private) && !(s.flags is Flags.Protected)
  
  lazy val publicConstructors: Iterable[Symbol] = {
    val ctors = targetType.typeSymbol.declarations
      .filter(isAccessibleConstructor)
      .filterNot(isPhantomConstructor)
    // log.withBlock(s"There are ${ctors.size} eligible constructors" ) { ctors.foreach(c => log(showConstructor(c))) }
    ctors
  }

  lazy val primaryConstructor: Option[Symbol] = targetType.typeSymbol.primaryConstructor match {
    case c if isAccessibleConstructor(c) => Some(c)
    case c => None
  }

  lazy val injectConstructors: Iterable[Symbol] = {
    val isInjectAnnotation = (a: Term) => a.tpe.typeSymbol.fullName == "javax.inject.Inject"
    val ctors = publicConstructors.filter(_.annotations.exists(isInjectAnnotation))
    log.withBlock(s"There are ${ctors.size} constructors annotated with @javax.inject.Inject" ) { ctors.foreach(c => log(showConstructor(c))) }
    ctors
  }

  lazy val injectConstructor: Option[Symbol] = if(injectConstructors.size > 1) abort(s"Ambiguous constructors annotated with @javax.inject.Inject for type [${targetType.typeSymbol.name}]") else injectConstructors.headOption

  lazy val constructor: Option[Symbol] = log.withBlock(s"Looking for constructor for $targetType"){
    val ctor = injectConstructor orElse primaryConstructor
    ctor.foreach(ctor => log(s"Found ${showConstructor(ctor)}"))
    ctor
  }

  //TODO
  // lazy val constructorParamLists: Option[List[List[Symbol]]] = constructor.map(_.termParamss.map(_.params.filterNot(_.headOption.exists(_.isImplicit))))
  lazy val constructorParamLists: Option[List[List[Symbol]]] = constructor.map(_.paramSymss)

  lazy val constructorArgs: Option[List[List[Term]]] = log.withBlock("Looking for targetConstructor arguments") {
    constructorParamLists.map(wireConstructorParamsWithImplicitLookups)
  }

  // lazy val constructorArgsWithImplicitLookups: Option[List[List[Tree]]] = log.withBlock("Looking for targetConstructor arguments with implicit lookups") {
  //   constructor.map(_.asMethod.paramLists).map(wireConstructorParamsWithImplicitLookups)
  // }

  lazy val constructorTree: Option[Tree] =  log.withBlock(s"Creating Constructor Tree for $targetType"){
    for {
      constructorValue <- constructor
      constructorArgsValue <- constructorArgs
    } yield {
      val constructionMethodTree: Term = Select(New(TypeIdent(targetType.typeSymbol)), constructorValue)
      constructorArgsValue.foldLeft(constructionMethodTree)((acc: Term, args: List[Term]) => Apply(acc, args))
    }
  }

  def wireConstructorParams(paramLists: List[List[Symbol]]): List[List[Term]] = paramLists.map(_.map(p => dependencyResolver.resolve(p, /*SI-4751*/paramType(p)) ))

  def wireConstructorParamsWithImplicitLookups(paramLists: List[List[Symbol]]): List[List[Term]] = paramLists.map(_.map {
    case i if i.flags is Flags.Implicit => resolveImplicitOrFail(i)
    case p => dependencyResolver.resolve(p, /*SI-4751*/ paramType(p))
  })

  private def resolveImplicitOrFail(param: Symbol): Term = Implicits.search(paramType(param)) match {
    case iss: ImplicitSearchSuccess => iss.tree
    case isf: ImplicitSearchFailure => report.throwError(s"Failed to resolve an implicit for [$param].")
  }

  private def paramType(param: Symbol): TypeRepr = {    
    // val (sym: Symbol, tpeArgs: List[Type]) = targetTypeD match {
    //   case TypeRef(_, sym, tpeArgs) => (sym, tpeArgs)
    //   case t => abort(s"Target type not supported for wiring: $t. Please file a bug report with your use-case.")
    // }
    // val pTpe = param.signature.substituteTypes(sym.asClass.typeParams, tpeArgs)
    // if (param.asTerm.isByNameParam) pTpe.typeArgs.head else pTpe
    
    //FIXME assertion error in test inheritanceHKT.success, selfTypeHKT.success
    Ref(param).tpe.widen
  }

  /**
    * In some cases there is one extra (phantom) constructor.
    * This happens when extended trait has implicit param:
    *
    * {{{
    *   trait A { implicit val a = ??? };
    *   class X extends A
    *   import scala.reflect.runtime.universe._
    *   typeOf[X].members.filter(m => m.isMethod && m.asMethod.isConstructor && m.asMethod.isPrimaryConstructor).map(_.asMethod.fullName)
    *
    *  //res1: Iterable[String] = List(X.<init>, A.$init$)
    *  }}}
    *
    *  The {{{A.$init$}}} is the phantom constructor and we don't want it.
    *
    *  In other words, if we don't filter such constructor using this function
    *  'wireActor-12-noPublicConstructor.failure' will compile and throw exception during runtime but we want to fail it during compilation time.
    */
  // def isPhantomConstructor(constructor: Symbol): Boolean = constructor.asMethod.fullName.endsWith("$init$")
  def isPhantomConstructor(constructor: Symbol): Boolean = constructor.fullName.endsWith("$init$")

  // def showConstructor(c: Symbol): String = c.asMethod.typeSignature.toString
  def showConstructor(c: Symbol): String = c.toString

  def abort(msg: String): Nothing = report.throwError(msg)
}
