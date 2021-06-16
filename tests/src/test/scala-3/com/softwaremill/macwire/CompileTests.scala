package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "companionFactoryAmbiguous" -> List("No public primary constructor found for", "Test.PrivConstructor",
                                          "and multiple matching apply methods in its companion object were found."),
      "explicitDepsNotWiredWithImplicitVals" -> List(valueNotFound("A")),
      "explicitDepsWiredWithImplicitValsFromMethodScope" -> List(ambiguousResMsg("A"), "dependency", "implicitDependencyA"),
      "importAmbiguous" -> List(ambiguousResMsg("A"), "myA", "theA"),
      "nestedMethodsWired" -> List(ambiguousResMsg("A"), "outerA", "innerA"),
      "nestedWithManyMatchingParamsWired" -> List(ambiguousResMsg("A"), "a1", "a2", "a3"),
      "multipleMethodParameters" -> List(ambiguousResMsg("A"), "a1", "a2"),
      "simpleValsMissingValue" -> List(valueNotFound("B")),
      "simpleValsDuplicateValue" -> List(ambiguousResMsg("B"), "theB1", "theB2"),
      "secondaryConstructorNotAnnotated" -> List(valueNotFound("String")),
      "phantomConstructor" -> List("Cannot find a public constructor nor a companion object for [Target]"),
      "companionObjectHasNoMethods" -> List("Companion object for",  "Target] has no apply methods constructing target type."),
      "companionObjectHasFakeApplyMethods" -> List("Companion object for",  "Target] has no apply methods constructing target type."),
      "toManyInjectAnnotations" -> List("Ambiguous constructors annotated with @javax.inject.Inject for type [Target]"),
      "nullaryMethodUsedAsCandidate" -> List("Found multiple values of type [A]: [List(foo, a)]"),
      "wireWithTwoParamsLists" -> List("found   : A => (B => __wrapper$1", "required: ? => __wrapper$1")
    ),
    expectedWarnings = List(
      // "forwardReferenceInBlock" -> List("Found [a] for parameter [a], but a forward reference [forwardA] was also eligible")
    )
  )
}
