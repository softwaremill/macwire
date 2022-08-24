package com.softwaremill.macwire

class CompileTests extends CompileTestsSupport {
  //FIXME we currently print only `@@` instead of full type name
  runTestsWith(
    expectedFailures = List(
      "methodWithTaggedParamsNotFound" -> List(valueNotFound("@@")),
      "methodWithTaggedParamsAmbiguous" -> List(ambiguousResMsg("@@"), "blueberryArg1", "blueberryArg2"),
      "taggedNoValueWithTag" -> List(valueNotFound("@@")))
  )
}
