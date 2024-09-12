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
      raw: Option[Expr[Any]]
  ) extends Provider
  // trait ClassOfProvider
  // trait MembersOfProvider

  private val classTypeRepr = TypeRepr.of[Class[_]]

  private val providersFromRawDependencies =
    @tailrec
    def createProviders(deps: List[Expr[Any]], acc: Vector[Provider], seenTpes: Set[TypeRepr]): Vector[Provider] =
      deps match
        case Nil => acc
        case dep :: otherDeps =>
          val term = dep.asTerm
          val tpe = term.tpe
          val providersToAdd = log.withBlock(s"processing dependency: ${showExprShort(dep)}"):
            if seenTpes.exists(seenTpe => seenTpe =:= tpe) then
              reportError(s"Duplicate type in dependencies list: ${showTypeName(tpe)}, for: ${showExprShort(dep)}.")

            val instanceProvider = InstanceProvider(tpe, Nil, _ => term, Some(dep))

            val factoryProvider =
              if term.tpe.isFunctionType then Vector() // TODO
              else Vector.empty

            val classOfProvider =
              if term.tpe <:< classTypeRepr then
                val classOfTypeParameter = term.tpe.baseType(classTypeRepr.typeSymbol).typeArgs.head
                log(s"detected a classOf provider: ${classOfTypeParameter.show}")
                creatorProviderForType(classOfTypeParameter).map(_.copy(raw = Some(dep))).toVector
              else Vector.empty

            Vector(instanceProvider) ++ factoryProvider ++ classOfProvider

          createProviders(otherDeps, acc ++ providersToAdd, seenTpes + tpe)

    createProviders(rawDependencies, Vector.empty, Set.empty)
  end providersFromRawDependencies

  private def creatorProviderForType(t: TypeRepr): Option[InstanceProvider] =
    Constructor
      .find[q.type](t, log, reportError)
      .orElse(Companion.find[q.type](t, log, reportError))
      .map: creator =>
        InstanceProvider(t, creator.paramFlatTypes, creator.applied, None)

  object Provider:
    def forType(t: TypeRepr): Option[Provider] =
      providersFromRawDependencies
        .find(_.tpe <:< t)
        .orElse:
          log("no provider foudn in raw depdencies, trying to create one using a constructor/apply method")
          creatorProviderForType(t)
