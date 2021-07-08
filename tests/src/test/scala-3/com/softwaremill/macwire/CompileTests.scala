package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "companionFactoryAmbiguous" -> List("No public primary constructor found for", "PrivConstructor",
                                          "and multiple matching apply methods in its companion object were found."),
      "explicitDepsNotWiredWithImplicitVals" -> List(valueNotFound("A")),
      "explicitDepsWiredWithImplicitValsFromMethodScope" -> List(ambiguousResMsg("A"), "dependency", "implicitDependencyA"),
      "nestedMethodsWired" -> List(ambiguousResMsg("A"), "outerA", "innerA"),
      "nestedWithManyMatchingParamsWired" -> List(ambiguousResMsg("A"), "a1", "a2", "a3"),
      "multipleMethodParameters" -> List(ambiguousResMsg("A"), "a1", "a2"),
      "simpleValsMissingValue" -> List(valueNotFound("B")),
      "simpleValsDuplicateValue" -> List(ambiguousResMsg("B"), "theB1", "theB2"),
      "secondaryConstructorNotAnnotated" -> List(valueNotFound("String")),
      "phantomConstructor" -> List("Cannot find a public constructor", "[Target]"),
      "companionObjectHasNoMethods" -> List("companion object",  "has no apply methods constructing target type", "[Target]"),
      "companionObjectHasFakeApplyMethods" -> List("companion object",  "has no apply methods constructing target type", "[Target]"),
      "toManyInjectAnnotations" -> List("Ambiguous constructors annotated with @javax.inject.Inject for type [Target]"),
      "wireWithTwoParamsLists" -> List("Found:    Main.A => Main.B => Main.Test.C", "Required: Any => Main.Test.C"),
      "wireRecEmptyString" -> List(valueNotFound("String"))
    ),
  )
}
