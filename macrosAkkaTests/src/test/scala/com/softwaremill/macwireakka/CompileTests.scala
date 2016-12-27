package com.softwaremill.macwireakka

import com.softwaremill.macwire.CompileTestsSupport

class CompileTests extends CompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "dummy" -> List(
        "not found: value XXX")
    ),
    expectedWarnings = List()
  )
}
