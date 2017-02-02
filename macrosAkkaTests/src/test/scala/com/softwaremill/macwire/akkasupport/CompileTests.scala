package com.softwaremill.macwire.akkasupport

import com.softwaremill.macwire.CompileTestsSupport

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "wireProps-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireAnonymousActor-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireActor-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireAnonymousActor-3.1-missingActorRefFactoryDependency" -> List("Cannot find a value of type: [akka.actor.ActorRefFactory]"),
      "wireActor-3.1-missingActorRefFactoryDependency" -> List("Cannot find a value of type: [akka.actor.ActorRefFactory]"),
      "wireProps-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireAnonymousActor-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireActor-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireProps-7-notActor" -> List("type arguments [NotActor] do not conform to macro method wireProps's type parameter bounds [T <: akka.actor.Actor]"),
      "wireAnonymousActor-7-notActor" -> List("type arguments [NotActor] do not conform to macro method wireAnonymousActor's type parameter bounds [T <: akka.actor.Actor]"),
      "wireActor-7-notActor" -> List("type arguments [NotActor] do not conform to macro method wireActor's type parameter bounds [T <: akka.actor.Actor]"),
      "wireProps-11-toManyInjectAnnotations" -> List("Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActor]"),
      "wireAnonymousActor-11-toManyInjectAnnotations" -> List("Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActor]"),
      "wireActor-11-toManyInjectAnnotations" -> List("Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActor]"),
      "wireProps-12-noPublicConstructor" -> List("Cannot find a public constructor for [SomeActor]"),
      "wireAnonymousActor-12-noPublicConstructor" -> List("Cannot find a public constructor for [SomeActor]"),
      "wireActor-12-noPublicConstructor" -> List("Cannot find a public constructor for [SomeActor]")
    ),
    expectedWarnings = List()
  )
}
