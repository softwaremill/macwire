package com.softwaremill.macwire.internals.autowire

import scala.quoted.*
import scala.annotation.tailrec

class AutowireProviders[Q <: Quotes](using val q: Q)(rawDependencies: List[Expr[Any]]):
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

  private val providersFromRawDependencies =
    @tailrec
    def createProviders(deps: List[Expr[Any]], acc: Vector[Provider], seenTpes: Set[TypeRepr]): Vector[Provider] =
      deps match
        case Nil => acc
        case dep :: otherDeps =>
          val term = dep.asTerm
          val tpe = term.tpe
          if seenTpes.exists(seenTpe => seenTpe =:= tpe) then
            report.errorAndAbort("Duplicate type $tpe in dependencies list, for: $dep!")

          val instanceProvider = InstanceProvider(tpe, Nil, _ => term, Some(dep))

          val factoryProvider =
            if term.tpe.isFunctionType then Vector() // TODO
            else Vector.empty

          createProviders(otherDeps, (acc :+ instanceProvider) ++ factoryProvider, seenTpes + tpe)

    createProviders(rawDependencies, Vector.empty, Set.empty)
  end providersFromRawDependencies

  object Provider:
    def forType(t: TypeRepr): Option[Provider] =
      providersFromRawDependencies
        .find(_.tpe <:< t)
        .orElse:
          Constructor
            .find[q.type](t, log)
            .map: constructor =>
              InstanceProvider(t, constructor.paramFlatTypes, constructor.applied, None)
