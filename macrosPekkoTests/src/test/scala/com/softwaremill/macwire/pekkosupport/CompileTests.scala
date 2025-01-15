package com.softwaremill.macwire.pekkosupport

import com.softwaremill.macwire.CompileTestsSupport

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "wireProps-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireAnonymousActor-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireActor-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireAnonymousActor-3.1-missingActorRefFactoryDependency" -> List(
        "Cannot find a value of type: [org.apache.pekko.actor.ActorRefFactory]"
      ),
      "wireActor-3.1-missingActorRefFactoryDependency" -> List(
        "Cannot find a value of type: [org.apache.pekko.actor.ActorRefFactory]"
      ),
      "wireProps-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireAnonymousActor-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireActor-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireProps-7-notActor" -> List(
        "type arguments [NotActor] do not conform to macro method wireProps's type parameter bounds [T <: org.apache.pekko.actor.Actor]"
      ),
      "wireAnonymousActor-7-notActor" -> List(
        "type arguments [NotActor] do not conform to macro method wireAnonymousActor's type parameter bounds [T <: org.apache.pekko.actor.Actor]"
      ),
      "wireActor-7-notActor" -> List(
        "type arguments [NotActor] do not conform to macro method wireActor's type parameter bounds [T <: org.apache.pekko.actor.Actor]"
      ),
      "wireProps-11-toManyInjectAnnotations" -> List(
        "Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActor]"
      ),
      "wireAnonymousActor-11-toManyInjectAnnotations" -> List(
        "Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActor]"
      ),
      "wireActor-11-toManyInjectAnnotations" -> List(
        "Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActor]"
      ),
      "wireProps-12-noPublicConstructor" -> List("Cannot find a public constructor for [SomeActor]"),
      "wireAnonymousActor-12-noPublicConstructor" -> List("Cannot find a public constructor for [SomeActor]"),
      "wireActor-12-noPublicConstructor" -> List("Cannot find a public constructor for [SomeActor]"),
      "wireActor-13-missingImplicitDependency" -> List("could not find implicit value for parameter e: D"),
      "wireAnonymousActor-13-missingImplicitDependency" -> List("could not find implicit value for parameter e: D"),
      "wireProps-13-missingImplicitDependency" -> List("could not find implicit value for parameter e: D"),
      "wireActorWithFactory-2-manyParameterLists" -> List("Not supported factory type: "),
      "wireActorWithFactory-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireActorWithFactory-7-notActorFactory" -> List(
        "overloaded method",
        "apply with alternatives:",
        "cannot be applied to (NotActor)"
      ),
      "wireActorWithFactory-12-privateMethod" -> List("method get in object SomeActor cannot be accessed"),
      "wireActorWithProducer-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireActorWithProducer-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wireActorWithProducer-7-notActorProducer" -> List("wireActorWith does not support the type: [NotProducer]"),
      "wireActorWithProducer-11-toManyInjectAnnotations" -> List(
        "Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActorProducer]"
      ),
      "wireActorWithProducer-12-noPublicConstructor" -> List(
        "Cannot find a public constructor for [SomeActorProducer]"
      ),
      "wireActorWithProducer-13-missingImplicitDependency" -> List("could not find implicit value for parameter e: D"),
      "wireAnonymousActorWithFactory-2-manyParameterLists" -> List("Not supported factory type: "),
      "wireAnonymousActorWithFactory-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireAnonymousActorWithFactory-7-notActorFactory" -> List(
        "overloaded method",
        "apply with alternatives:",
        "cannot be applied to (NotActor)"
      ),
      "wireAnonymousActorWithFactory-12-privateMethod" -> List("method get in object SomeActor cannot be accessed"),
      "wireAnonymousActorWithProducer-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wireAnonymousActorWithProducer-6-injectAnnotationButNoDependencyInScope" -> List(
        "Cannot find a value of type: [C]"
      ),
      "wireAnonymousActorWithProducer-7-notActorProducer" -> List(
        "wireAnonymousActorWith does not support the type: [NotProducer]"
      ),
      "wireAnonymousActorWithProducer-11-toManyInjectAnnotations" -> List(
        "Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActorProducer]"
      ),
      "wireAnonymousActorWithProducer-12-noPublicConstructor" -> List(
        "Cannot find a public constructor for [SomeActorProducer]"
      ),
      "wireAnonymousActorWithProducer-13-missingImplicitDependency" -> List(
        "could not find implicit value for parameter e: D"
      ),
      "wirePropsWithFactory-2-manyParameterLists" -> List("Not supported factory type: "),
      "wirePropsWithFactory-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wirePropsWithFactory-7-notActorFactory" -> List(
        "overloaded method",
        "apply with alternatives:",
        "cannot be applied to (NotActor)"
      ),
      "wirePropsWithFactory-12-privateMethod" -> List("method get in object SomeActor cannot be accessed"),
      "wirePropsWithProducer-3-missingDependency" -> List("Cannot find a value of type: [A]"),
      "wirePropsWithProducer-6-injectAnnotationButNoDependencyInScope" -> List("Cannot find a value of type: [C]"),
      "wirePropsWithProducer-7-notActorProducer" -> List("wirePropsWith does not support the type: [NotProducer]"),
      "wirePropsWithProducer-11-toManyInjectAnnotations" -> List(
        "Ambiguous constructors annotated with @javax.inject.Inject for type [SomeActorProducer]"
      ),
      "wirePropsWithProducer-12-noPublicConstructor" -> List(
        "Cannot find a public constructor for [SomeActorProducer]"
      ),
      "wirePropsWithProducer-13-missingImplicitDependency" -> List("could not find implicit value for parameter e: D")
    ),
    expectedWarnings = List()
  )
}
