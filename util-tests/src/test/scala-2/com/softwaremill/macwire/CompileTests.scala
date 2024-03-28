package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "methodWithTaggedParamsNotFound" -> List(valueNotFound("Berry @@ Blue")),
      "methodWithTaggedParamsAmbiguous" -> List(ambiguousResMsg("Berry @@ Blue"), "blueberryArg1", "blueberryArg2"),
      "moduleAmbiguousWithParent" -> List(ambiguousResMsg("A"), "module.a", "parentA"),
      "taggedNoValueWithTag" -> List(valueNotFound("Berry @@ Blue"))
    )
  )
}
