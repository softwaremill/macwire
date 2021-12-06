package com.softwaremill.macwire.internals

import scala.reflect.macros.blackbox

private[macwire] class ConstructorCrimper[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, log: Logger) {
  import c.universe._

  type DependencyResolverType = DependencyResolver[c.type, Type, Tree]

  lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe

  // We need to get the "real" type in case the type parameter is a type alias - then it cannot
  // be directly instantiated
  lazy val targetTypeD: Type = targetType.dealias

  lazy val constructor: Option[Symbol] = ConstructorCrimper.constructor(c, log)(targetType)

  def constructorArgsWithImplicitLookups(dependencyResolver: DependencyResolverType): Option[List[List[Tree]]] =
    log.withBlock("Looking for targetConstructor arguments with implicit lookups") {
      constructor.map(_.asMethod.paramLists).map(wireConstructorParamsWithImplicitLookups(dependencyResolver))
    }

  def constructorTree(dependencyResolver: DependencyResolverType): Option[Tree] =
    ConstructorCrimper.constructorTree(c, log)(targetType, dependencyResolver.resolve(_, _))

  def wireConstructorParams(
      dependencyResolver: DependencyResolverType
  )(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.map(
    _.map(p => dependencyResolver.resolve(p, /*SI-4751*/ ConstructorCrimper.paramType(c)(targetTypeD, p)))
  )

  def wireConstructorParamsWithImplicitLookups(
      dependencyResolver: DependencyResolverType
  )(paramLists: List[List[Symbol]]): List[List[Tree]] = paramLists.map(_.map {
    case i if i.isImplicit => q"implicitly[${ConstructorCrimper.paramType(c)(targetType, i)}]"
    case p                 => dependencyResolver.resolve(p, /*SI-4751*/ ConstructorCrimper.paramType(c)(targetTypeD, p))
  })

}

object ConstructorCrimper {
  def showConstructor[C <: blackbox.Context](c: C)(s: c.Symbol): String = s.asMethod.typeSignature.toString

  private def constructor[C <: blackbox.Context](c: C, log: Logger)(targetType: c.Type) = {
    import c.universe._

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
      * In other words, if we don't filter such constructor using this function
      * 'wireActor-12-noPublicConstructor.failure' will compile and throw exception during runtime but we want to fail
      * it during compilation time.
      */
    def isPhantomConstructor(constructor: Symbol): Boolean = constructor.asMethod.fullName.endsWith("$init$")

    lazy val publicConstructors: Iterable[Symbol] = {
      val ctors = targetType.members
        .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
        .filterNot(isPhantomConstructor)
      log.withBlock(s"There are ${ctors.size} eligible constructors") { ctors.foreach(s => log(showConstructor(c)(s))) }
      ctors
    }

    lazy val primaryConstructor: Option[Symbol] = publicConstructors.find(_.asMethod.isPrimaryConstructor)

    lazy val injectConstructors: Iterable[Symbol] = {
      val isInjectAnnotation = (a: Annotation) => a.toString == "javax.inject.Inject"
      val ctors = publicConstructors.filter(_.annotations.exists(isInjectAnnotation))
      log.withBlock(s"There are ${ctors.size} constructors annotated with @javax.inject.Inject") {
        ctors.foreach(s => log(showConstructor(c)(s)))
      }
      ctors
    }

    lazy val injectConstructor: Option[Symbol] =
      if (injectConstructors.size > 1)
        c.abort(
          c.enclosingPosition,
          s"Ambiguous constructors annotated with @javax.inject.Inject for type [$targetType]"
        )
      else injectConstructors.headOption

    log.withBlock(s"Looking for constructor for $targetType") {
      val ctor = injectConstructor orElse primaryConstructor
      ctor.foreach(ctor => log(s"Found ${showConstructor(c)(ctor)}"))
      ctor
    }
  }

  //TODO move to the package object
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

  def constructorTree[C <: blackbox.Context](
      c: C,
      log: Logger
  )(targetType: c.Type, resolver: (c.Symbol, c.Type) => c.Tree): Option[c.Tree] =
    constructorFactory(c, log)(targetType).map { case (paramLists, factory) =>
      import c.universe._

      lazy val targetTypeD: Type = targetType.dealias

      def wireConstructorParams(paramLists: List[List[Symbol]]): List[List[Tree]] =
        paramLists.map(_.map(p => resolver(p, /*SI-4751*/ paramType(c)(targetTypeD, p))))

      def constructorArgs: List[List[Tree]] = log.withBlock("Looking for targetConstructor arguments") {
        wireConstructorParams(paramLists)
      }

      factory(constructorArgs)
    }

  def constructorFactory[C <: blackbox.Context](
      c: C,
      log: Logger
  )(targetType: c.Type): Option[(List[List[c.Symbol]], List[List[c.Tree]] => c.Tree)] = {
    import c.universe._

    lazy val targetTypeD: Type = targetType.dealias

    val constructor: Option[Symbol] = ConstructorCrimper.constructor(c, log)(targetType)

    val constructorParamLists: Option[List[List[Symbol]]] =
      constructor.map(_.asMethod.paramLists.filterNot(_.headOption.exists(_.isImplicit)))

    def factory(constructorArgs: List[List[Tree]]): Tree = {
      val constructionMethodTree: Tree = Select(New(Ident(targetTypeD.typeSymbol)), termNames.CONSTRUCTOR)
      constructorArgs.foldLeft(constructionMethodTree)((acc: Tree, args: List[Tree]) => Apply(acc, args))
    }

    constructorParamLists.map(cpl => (cpl, factory(_)))
  }

}
