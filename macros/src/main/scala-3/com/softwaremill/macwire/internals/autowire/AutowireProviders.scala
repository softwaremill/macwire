package com.softwaremill.macwire.internals.autowire

import scala.quoted.*
import scala.annotation.tailrec
import com.softwaremill.macwire.internals.showTypeName
import com.softwaremill.macwire.internals.showExprShort

class AutowireProviders[Q <: Quotes](using val q: Q)(
    rawDependencies: List[Expr[Any]],
    reportError: ReportError[q.type]
):
  import q.reflect.*

  /** Providers define how to create an instance of a type, and what dependencies are needed. Used to create graph
    * nodes, which contain generated wiring code fragments.
    */
  trait Provider:
    /** The type of instances that the provider providers. */
    def tpe: TypeRepr

    /** The types of dependencies that need to be provided, in order to create an instance of `tpe`. */
    def dependencies: List[TypeRepr]

    /** Given a list of terms, one corresponding to each dependency, returns a terms which creates an instance of
      * [[tpe]].
      */
    def create: List[Term] => Term

    /** The raw dependency as provided by the user, if any. */
    def raw: Option[Expr[Any]]

  private case class InstanceProvider(
      tpe: TypeRepr,
      dependencies: List[TypeRepr],
      create: List[Term] => Term,
      raw: Option[Expr[Any]] = None
  ) extends Provider

  private val classTypeRepr = TypeRepr.of[Class[_]]

  private val providersFromRawDependencies =
    @tailrec
    def createProviders(deps: List[Expr[Any]], acc: Vector[Provider], seenTpes: Set[TypeRepr]): Vector[Provider] =
      deps match
        case Nil => acc
        case dep :: otherDeps =>
          val term = dep.asTerm
          val tpe = term.tpe.dealias.widen // dealias, and widen from singleton types
          val providersToAdd = log.withBlock(s"processing dependency: ${showExprShort(dep)}"):
            if seenTpes.exists(seenTpe => seenTpe =:= tpe) then
              reportError(s"Duplicate type in dependencies list: ${showTypeName(tpe)}, for: ${showExprShort(dep)}.")

            val instanceProvider = InstanceProvider(tpe, Nil, _ => term)

            val factoryProvider =
              if tpe.isFunctionType then Vector(providerFromFunction(term, tpe))
              else Vector.empty

            val classOfProvider =
              if term.tpe <:< classTypeRepr then
                val classOfTypeParameter = term.tpe.baseType(classTypeRepr.typeSymbol).typeArgs.head
                log(s"detected a classOf provider: ${classOfTypeParameter.show}")
                creatorProviderForType(classOfTypeParameter).toVector
              else Vector.empty

            val membersOfProviders =
              if term.show.startsWith("com.softwaremill.macwire.macwire$package.membersOf") then
                providersFromMembersOf(term)
              else Vector.empty

            Vector(instanceProvider) ++ factoryProvider ++ classOfProvider ++ membersOfProviders

          createProviders(otherDeps, acc ++ providersToAdd.map(_.copy(raw = Some(dep))), seenTpes + tpe)

    createProviders(rawDependencies, Vector.empty, Set.empty)
  end providersFromRawDependencies

  private def providerFromFunction(t: Term, tpe: TypeRepr): InstanceProvider =
    val typeArgs = tpe.typeArgs
    val depTypes = typeArgs.init
    val resultType = typeArgs.last.dealias.widen // the types provided by dependencies should always be possibly general
    log(s"detected a function provider, for: ${resultType.show}, deps: ${depTypes.map(_.show)}")
    val createInstance = (deps: List[Term]) => Apply(Select.unique(t, "apply"), deps)
    InstanceProvider(resultType, depTypes, createInstance)

  private def providersFromMembersOf(t: Term): Vector[InstanceProvider] =
    def nonSyntethic(member: Symbol): Boolean =
      !member.fullName.startsWith("java.lang.Object") &&
        !member.fullName.startsWith("scala.Any") &&
        !member.fullName.startsWith("scala.AnyRef") &&
        !member.fullName.endsWith("<init>") &&
        !member.fullName.endsWith("$init$") &&
        !member.fullName.contains("$default$") && // default params for copy on case classes
        !member.fullName.matches(".*_\\d+") // tuple methods on case classes

    def isPublic(member: Symbol): Boolean = !((member.flags is Flags.Private) || (member.flags is Flags.Protected))

    log.withBlock(s"detected a membersOf provider"):
      val Apply(_, List(membersOf)) = t: @unchecked
      val membersOfSymbol = membersOf.tpe.typeSymbol
      (membersOfSymbol.fieldMembers ++ membersOfSymbol.methodMembers).toVector
        .filter(m => nonSyntethic(m) && isPublic(m))
        .flatMap { s =>
          if s.isValDef then
            log(s"found a value member: ${s.typeRef.show}")
            Some(InstanceProvider(s.typeRef, Nil, _ => Select(membersOf, s)))
          else if s.isDefDef && s.paramSymss.isEmpty then
            log(s"found a no-arg method member: ${s.typeRef.show}")
            Some(InstanceProvider(s.typeRef, Nil, _ => Select(membersOf, s)))
          else None
        }
  end providersFromMembersOf

  /** For the given type, try to create a provider based on a constructor or apply method. */
  private def creatorProviderForType(t: TypeRepr): Option[InstanceProvider] =
    Constructor
      .find[q.type](t, log, reportError)
      .orElse(Companion.find[q.type](t, log, reportError))
      .map: creator =>
        InstanceProvider(t, creator.paramFlatTypes, creator.applied)

  object Provider:
    def forType(t: TypeRepr): Option[Provider] =
      providersFromRawDependencies
        .find(_.tpe <:< t)
        .map(p => { log(s"found provider in rawDependencies for ${showTypeName(t)}"); p })
        .orElse:
          log("no provider found in raw depdencies, trying to create one using a constructor/apply method")
          creatorProviderForType(t)
