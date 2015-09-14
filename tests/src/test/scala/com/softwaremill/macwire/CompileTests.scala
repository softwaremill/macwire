package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {

  runTestsWithExpectedFailures(
    "explicitDepsNotWiredWithImplicitVals" -> List(valueNotFound("A")),
    "explicitDepsWiredWithImplicitValsFromMethodScope" -> List(ambiguousResMsg("A"), "dependency", "implicitDependencyA"),
    "importAmbiguous" -> List(ambiguousResMsg("A"), "myA", "theA"),
    "nestedMethodsWired" -> List(ambiguousResMsg("A"), "outerA", "innerA"),
    "nestedWithManyMatchingParamsWired" -> List(ambiguousResMsg("A"), "a1", "a2", "a3"),
    "multipleMethodParameters" -> List(ambiguousResMsg("A"), "a1", "a2"),
    "simpleValsMissingValue" -> List(valueNotFound("B")),
    "simpleValsDuplicateValue" -> List(ambiguousResMsg("B"), "theB1", "theB2")
  )
}
