package com.softwaremill.macwire

class AutowireCompileTests extends CompileTestsSupport:
  override protected val testsSubdirectory = Some("autowire")

  runTestsWith(
    expectedFailures = List(
      "privateConstructor" -> List(
        "cannot find a provided dependency, public constructor or public apply method for: A;",
        "wiring path: A"
      ),
      "cyclicDependency" -> List("cyclic dependencies detected;", "wiring path: A -> B -> A"),
      "primitives" -> List("cannot use a primitive type or String in autowiring;", "wiring path: A -> String"),
      "unusedDependency" -> List("unused dependencies: C.apply()"),
      "duplicate" -> List("duplicate type in dependencies list: A, for: a"),
      "plainTrait" -> List(
        "cannot find a provided dependency, public constructor or public apply method for: A;",
        "wiring path: B -> A"
      ),
      "autowireMembersOfDuplicate" -> List("duplicate type in dependencies list: A, for: new A()")
    )
  )
