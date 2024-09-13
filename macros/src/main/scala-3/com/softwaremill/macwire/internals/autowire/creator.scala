package com.softwaremill.macwire.internals.autowire

import com.softwaremill.macwire.internals.*
import scala.quoted.Quotes

/** A creator is either a constructor, or an apply method. The code they generate only differs in the
  * [[selectQualifier]]: either a `new` or a `ref`.
  */
class Creator[Q <: Quotes](using val q: Q)(
    selectQualifier: q.reflect.Term,
    creatorSymbol: q.reflect.Symbol,
    reportError: ReportError[q.type]
):
  import q.reflect.*

  private val paramSymbolsLists: List[List[Symbol]] = creatorSymbol.paramSymss

  private def isImplicit(f: Flags): Boolean = f.is(Flags.Implicit) || f.is(Flags.Given)

  // all non-implicit parmaters
  val paramFlatTypes: List[TypeRepr] = paramSymbolsLists.flatMap(
    _.flatMap(param => if isImplicit(param.flags) || param.isType then None else Some(paramType(param)))
  )

  /** Creates a term which corresponds to invoking the creator using the given parameters. Each term in the
    * [[paramFlatValues]] list should correspond to the corresponding type returned by [[paramFlatTypes]].
    */
  def applied(paramFlatValues: List[Term]): Term =
    val paramsFlatValuesIterator = paramFlatValues.iterator
    val paramValuesLists = paramSymbolsLists.map { paramSymbolList =>
      paramSymbolList.map { paramSymbol =>
        if isImplicit(paramSymbol.flags)
        then resolveImplicitOrFail(paramSymbol)
        else paramsFlatValuesIterator.next()
      }
    }
    val newTree: Term = Select(selectQualifier, creatorSymbol)
    paramValuesLists.foldLeft(newTree)((acc: Term, args: List[Term]) => Apply(acc, args))

  private def resolveImplicitOrFail(param: Symbol): Term = Implicits.search(paramType(param)) match {
    case iss: ImplicitSearchSuccess => iss.tree
    case isf: ImplicitSearchFailure => reportError(isf.explanation)
  }

  private def paramType(param: Symbol): TypeRepr = Ref(param).tpe.widen.dealias

object Constructor:
  def find[Q <: Quotes](using
      q: Q
  )(forType: q.reflect.TypeRepr, log: Logger, reportError: ReportError[q.type]): Option[Creator[q.type]] =
    log.withBlock(s"looking for constructor for ${showTypeName(forType)}"):
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

      if forType.typeSymbol.flags is Flags.Trait then None
      else
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
            reportError(
              s"multiple constructors annotated with @javax.inject.Inject for type: ${showTypeName(forType)}"
            )
          else injectConstructors.headOption

        (injectConstructor orElse primaryConstructor).map: ctor =>
          log(s"found $ctor")
          Creator[q.type](New(TypeIdent(forType.typeSymbol)), ctor, reportError)

object Companion:
  def find[Q <: Quotes](using
      q: Q
  )(forType: q.reflect.TypeRepr, log: Logger, reportError: ReportError[q.type]): Option[Creator[q.type]] =
    log.withBlock("looking for apply methods in companion object"):
      import q.reflect.*

      val companionType: Option[Symbol] = forType.typeSymbol.companionClass match
        case c if c == Symbol.noSymbol => None
        case c                         => Some(c)

      def returnType(symbol: Symbol): TypeRepr = symbol.tree match
        case dd: DefDef => dd.returnTpt.tpe

      def isCompanionApply(method: Symbol): Boolean =
        method.isDefDef &&
          !(method.flags is Flags.Private) &&
          !(method.flags is Flags.Protected) &&
          returnType(method) <:< forType &&
          method.name == "apply"

      val applies: List[Symbol] =
        val as: List[Symbol] = companionType.toList.flatMap(_.declarations.filter(isCompanionApply).toList)
        log.withBlock(s"there are ${as.size} apply methods") { as.foreach(c => log(c.toString)) }
        as

      val apply: Option[Symbol] =
        if applies.size > 1 then reportError(s"multiple public apply methods for type: ${showTypeName(forType)}")
        else applies.headOption

      apply.map: ctor =>
        log(s"found $ctor")
        Creator[q.type](Ref(forType.typeSymbol.companionModule), ctor, reportError)
