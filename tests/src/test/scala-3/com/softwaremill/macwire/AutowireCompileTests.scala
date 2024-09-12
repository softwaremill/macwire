package com.softwaremill.macwire

class AutowireCompileTests extends CompileTestsSupport:
  override protected val testsSubdirectory = Some("autowire")

  runTestsWith(
    expectedFailures = List(
      "privateConstructor" -> List(
        "Cannot find a provided dependency, public constructor or public apply method for: A.",
        "Wiring path: A."
      ),
      "cyclicDependency" -> List("Cyclic dependencies detected.", "Wiring path: A -> B -> A."),
      "primitives" -> List("Cannot use a primitive type or String in autowiring.", "Wiring path: A -> String."),
      "unusedDependency" -> List("Unused dependencies: C.apply()."),
      "duplicate" -> List("Duplicate type in dependencies list: A, for: a."),
      "plainTrait" -> List(
        "Cannot find a provided dependency, public constructor or public apply method for: A.",
        "Wiring path: B -> A."
      ),
      "membersOfDuplicate" -> List("Duplicate type in dependencies list: A, for: new A().")
    )
  )
