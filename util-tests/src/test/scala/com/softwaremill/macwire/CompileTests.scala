package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "methodWithTaggedParamsNotFound" -> List(valueNotFound("com.softwaremill.tagging.@@[Berry,Blue]")),
      "methodWithTaggedParamsAmbiguous" -> List(ambiguousResMsg("com.softwaremill.tagging.@@[Berry,Blue]"), "blueberryArg1", "blueberryArg2"),
      "moduleAmbiguousWithParent" -> List(ambiguousResMsg("A"), "module.a", "parentA"),
      "taggedNoValueWithTag" -> List(valueNotFound("com.softwaremill.tagging.@@[Berry,Blue]")))
  )
}
