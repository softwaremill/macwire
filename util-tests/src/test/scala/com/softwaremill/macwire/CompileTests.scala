package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "methodWithTaggedParamsNotFound" -> List(valueNotFound("com.softwaremill.macwire.tagging.@@[Berry,Blue]")),
      "methodWithTaggedParamsAmbiguous" -> List(ambiguousResMsg("com.softwaremill.macwire.tagging.@@[Berry,Blue]"), "blueberryArg1", "blueberryArg2"),
      "taggedNoValueWithTag" -> List(valueNotFound("com.softwaremill.macwire.tagging.@@[Berry,Blue]")))
  )
}
