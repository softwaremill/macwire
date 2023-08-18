package com.softwaremill.macwire

import org.apache.pekko.actor.{Actor, ActorRef, IndirectActorProducer, Props}

import scala.language.experimental.macros

package object pekkosupport {
  def wireProps[T <: Actor]: Props = macro MacwirePekkoMacros.wireProps_Impl[T]
  def wireAnonymousActor[T <: Actor]: ActorRef = macro MacwirePekkoMacros.wireAnonymousActor_Impl[T]
  def wireActor[T <: Actor](name: String): ActorRef = macro MacwirePekkoMacros.wireActor_Impl[T]

  def wirePropsWith[T]: Props = macro MacwirePekkoMacros.wirePropsWithProducer_Impl[T]
  def wireAnonymousActorWith[T]: ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithProducer_Impl[T]
  def wireActorWith[T](name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithProducer_Impl[T]

  def wirePropsWith[RES](factory: () => RES): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, RES](factory: (A) => RES): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, RES](factory: (A, B) => RES): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, RES](factory: (A, B, C) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, RES](factory: (A, B, C, D) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, RES](factory: (A, B, C, D, E) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, RES](factory: (A, B, C, D, E, F) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, RES](factory: (A, B, C, D, E, F, G) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, RES](factory: (A, B, C, D, E, F, G, H) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, RES](factory: (A, B, C, D, E, F, G, H, I) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, RES](factory: (A, B, C, D, E, F, G, H, I, J) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, RES](factory: (A, B, C, D, E, F, G, H, I, J, K) => RES): Props =
    macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]
  def wirePropsWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => RES
  ): Props = macro MacwirePekkoMacros.wirePropsWithFactory_Impl[RES]

  def wireAnonymousActorWith[RES](factory: () => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, RES](factory: (A) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, RES](factory: (A, B) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, RES](factory: (A, B, C) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, RES](factory: (A, B, C, D) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, RES](factory: (A, B, C, D, E) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, RES](factory: (A, B, C, D, E, F) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, RES](factory: (A, B, C, D, E, F, G) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, RES](factory: (A, B, C, D, E, F, G, H) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, RES](factory: (A, B, C, D, E, F, G, H, I) => RES): ActorRef =
    macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, RES](
      factory: (A, B, C, D, E, F, G, H, I, J) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]
  def wireAnonymousActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => RES
  ): ActorRef = macro MacwirePekkoMacros.wireAnonymousActorWithFactory_Impl[RES]

  def wireActorWith[RES](factory: () => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, RES](factory: (A) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, RES](factory: (A, B) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, RES](factory: (A, B, C) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, RES](factory: (A, B, C, D) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, RES](factory: (A, B, C, D, E) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, RES](factory: (A, B, C, D, E, F) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, RES](factory: (A, B, C, D, E, F, G) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, RES](factory: (A, B, C, D, E, F, G, H) => RES)(name: String): ActorRef =
    macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, RES](factory: (A, B, C, D, E, F, G, H, I) => RES)(
      name: String
  ): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, RES](factory: (A, B, C, D, E, F, G, H, I, J) => RES)(
      name: String
  ): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, RES](factory: (A, B, C, D, E, F, G, H, I, J, K) => RES)(
      name: String
  ): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, RES](factory: (A, B, C, D, E, F, G, H, I, J, K, L) => RES)(
      name: String
  ): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
  def wireActorWith[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, RES](
      factory: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => RES
  )(name: String): ActorRef = macro MacwirePekkoMacros.wireActorWithFactory_Impl[RES]
}
