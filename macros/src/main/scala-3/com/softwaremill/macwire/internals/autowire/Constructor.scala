package com.softwaremill.macwire.internals.autowire

import com.softwaremill.macwire.internals.*
import scala.quoted.Quotes

class Constructor[Q <: Quotes](using val q: Q)(
    constructorSymbol: q.reflect.Symbol,
    forType: q.reflect.TypeRepr
):
  import q.reflect.*

  private val paramSymbolsLists: List[List[Symbol]] = constructorSymbol.paramSymss

  // all non-implicit parmaters
  val paramFlatTypes: List[TypeRepr] = paramSymbolsLists.flatMap(
    _.flatMap(param => if param.flags is Flags.Implicit then None else Some(paramType(param)))
  )

  /** Creates a term which corresponds to invoking the constructor using the given parameters. Each term in the
    * [[paramFlatValues]] list should correspond to the corresponding type returned by [[paramFlatTypes]].
    */
  def applied(paramFlatValues: List[Term]): Term =
    val paramsFlatValuesIterator = paramFlatValues.iterator
    val paramValuesLists = paramSymbolsLists.map { paramSymbolList =>
      paramSymbolList.map { paramSymbol =>
        if paramSymbol.flags is Flags.Implicit
        then resolveImplicitOrFail(paramSymbol)
        else paramsFlatValuesIterator.next()
      }
    }
    val newTree: Term = Select(New(TypeIdent(forType.typeSymbol)), constructorSymbol)
    paramValuesLists.foldLeft(newTree)((acc: Term, args: List[Term]) => Apply(acc, args))

  private def resolveImplicitOrFail(param: Symbol): Term = Implicits.search(paramType(param)) match {
    case iss: ImplicitSearchSuccess => iss.tree
    case isf: ImplicitSearchFailure => report.errorAndAbort(s"Failed to resolve an implicit for [$param].")
  }

  private def paramType(param: Symbol): TypeRepr = Ref(param).tpe.widen.dealias

object Constructor:
  def find[Q <: Quotes](using q: Q)(forType: q.reflect.TypeRepr, log: Logger): Option[Constructor[Q]] =
    import q.reflect.*

    def isAccessibleConstructor(s: Symbol) =
      s.isClassConstructor && !(s.flags is Flags.Private) && !(s.flags is Flags.Protected)

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
    def isPhantomConstructor(constructor: Symbol): Boolean = constructor.fullName.endsWith("$init$")

    val publicConstructors: Iterable[Symbol] =
      val ctors = forType.typeSymbol.declarations
        .filter(isAccessibleConstructor)
        .filterNot(isPhantomConstructor)
      log.withBlock(s"there are ${ctors.size} eligible constructors") { ctors.foreach(c => log(c.toString)) }
      ctors

    val primaryConstructor: Option[Symbol] = forType.typeSymbol.primaryConstructor match
      case c if isAccessibleConstructor(c) => Some(c)
      case c                               => None

    val injectConstructors: Iterable[Symbol] =
      val isInjectAnnotation = (a: Term) => a.tpe.typeSymbol.fullName == "javax.inject.Inject"
      val ctors = publicConstructors.filter(_.annotations.exists(isInjectAnnotation))
      log.withBlock(s"there are ${ctors.size} constructors annotated with @javax.inject.Inject") {
        ctors.foreach(c => log(c.toString))
      }
      ctors

    val injectConstructor: Option[Symbol] =
      if injectConstructors.size > 1 then
        report.errorAndAbort(
          s"Multiple constructors annotated with @javax.inject.Inject for type: ${showTypeName(forType)}."
        )
      else injectConstructors.headOption

    log.withBlock(s"looking for constructor for ${showTypeName(forType)}"):
      (injectConstructor orElse primaryConstructor).map: ctor =>
        log(s"found $ctor")
        Constructor[Q](ctor, forType)
