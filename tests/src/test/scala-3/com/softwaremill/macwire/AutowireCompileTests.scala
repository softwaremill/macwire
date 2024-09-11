package com.softwaremill.macwire

class AutowireCompileTests extends CompileTestsSupport:
  override protected val testsSubdirectory = Some("autowire")

  runTestsWith(
    expectedFailures = List(
      "privateConstructor" -> List(
        "Cannot find a provided dependency, public constructor or public apply method for: A.",
        "Wiring path: A"
      ),
      "cyclicDependency" -> List("Cyclic dependencies detected.", "Wiring path: A -> B -> A"),
      "primitives" -> List("Cannot use a primitive type or String in autowiring.", "Wiring path: A -> String"),
      "unusedDependency" -> List("Unused dependencies: C.apply().")
    )
  )
