package com.softwaremill.macwire.autocats

import com.softwaremill.macwire.CatsAutowireCompileTestsSupport

class CompileTests extends CatsAutowireCompileTestsSupport {

  runTestsWith(
    expectedFailures = List(
      "simpleMissingDeps" -> List("Failed to create an instance of [B].", "Cannot construct instance of [String] on path [constructor B].a -> [constructor A].s"),
      "simpleMissingMultiLevelDeps" -> List("Failed to create an instance of [C].", "Cannot construct instance of [String] on path [constructor C].b -> [constructor B].a -> [constructor A].s"),
      "notUsedProvider" -> List("Not used providers for the following types [D]"),
      "ambiguousInstances" -> List("Ambiguous instances of types [A]"),
      "ambiguousTraitImplementations" -> List("Ambiguous instances of types [A, B]"),
      "constructInputProvider" -> List("Failed to create an instance of [B].", "Cannot construct instance of [String] on path [constructor B].a -> [constructor A].s"),//TODO we should add a warning in this case.
      "missingConstructorDependencies" -> List("Failed to create an instance of [C].", "Cannot construct instance of [Int] on path [constructor C].b -> [constructor B].i"),
      "missingApplyDependencies" -> List("Failed to create an instance of [", "C].", "Cannot construct instance of [Int] on path [constructor C].b -> [method apply].i"),
      "missingFactoryMethodDependencies" -> List("Failed to create an instance of [C].", "Cannot construct instance of [Int] on path [constructor C].b -> [method makeB].i"),
    ),
    expectedWarnings = List()
  )
}
