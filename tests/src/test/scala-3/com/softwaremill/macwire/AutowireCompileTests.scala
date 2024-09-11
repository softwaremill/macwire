package com.softwaremill.macwire

class AutowireCompileTests extends CompileTestsSupport:
  override protected val testsSubdirectory = Some("autowire")

  runTestsWith(
    expectedFailures = List(
      "privateConstructor" -> List("Cannot find a dependency or a public constructor for: A.", "Wiring path: A"),
      "cyclicDependency" -> List("Cyclic dependencies detected.", "Wiring path: A -> B -> A"),
      "primitives" -> List("Cannot use a primitive type or String in autowiring.", "Wiring path: A -> String")
    )
  )
