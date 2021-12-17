package com.softwaremill.macwire.autocats

import com.softwaremill.macwire.CatsAutowireCompileTestsSupport

class CompileTests extends CatsAutowireCompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "simpleMissingDeps" -> List("Cannot find a value of type: [B]"), //TODO List("Cannot find a value of type: [String], path: B.s")
      "simpleMissingMultiLevelDeps" -> List("Cannot find a value of type: [C]"), //TODO List("Cannot find a value of type: [String], path: C.b.a.s")
      "notUsedProvider" -> List("Not used providers for the following types [D]"),
      "ambiguousInstances" -> List("Ambiguous instances of types [A]"),
      "ambiguousTraitImplementations" -> List("Ambiguous instances of types [A, B]")
    ),
    expectedWarnings = List()
  )
}
