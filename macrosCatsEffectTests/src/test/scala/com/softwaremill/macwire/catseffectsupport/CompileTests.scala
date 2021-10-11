package com.softwaremill.macwire.catseffectsupport

import com.softwaremill.macwire.CompileTestsSupport

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "wireApp-missing-deps" -> List("Cannot find a value of type: [String]")
    ),
    expectedWarnings = List()
  )
}
