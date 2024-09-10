package com.softwaremill.macwire

class AutowireCompileTests extends CompileTestsSupport:
  override protected val testsSubdirectory = Some("autowire")

  runTestsWith(
    expectedFailures = List(
      "privateConstructor" -> List("Cannot find a dependency or a public constructor for: A, while wiring: A")
    )
  )
