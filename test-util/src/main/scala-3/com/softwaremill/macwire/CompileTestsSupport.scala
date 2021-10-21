package com.softwaremill.macwire

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

import scala.io.Source
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

import dotty.tools.dotc.reporting.{ThrowingReporter, Diagnostic}
import dotty.tools.dotc.Driver
import java.io.IOException

trait CompileTestsSupport extends BaseCompileTestsSupport with OptionValues {
  type ExpectedFailures = List[String]

  override val ignoreSuffixes: List[String] = List(".scala2")

  private def withTempFile(content: String)(f: Path => Unit) = {
    var tempFile: Path = null
    try {
      tempFile = Files.createTempFile("macwire", ".scala")
      Files.write(tempFile, content.getBytes, StandardOpenOption.WRITE)

      f(tempFile)
    } finally {
      Files.delete(tempFile)
    }
  }

  override def addTest(
      testName: String,
      ignored: Boolean,
      expectedFailures: ExpectedFailures,
      expectedWarningsFragments: List[String],
      imports: String = GlobalImports
  ) = {
    behavior of testName
    val description = (if (expectedFailures.isEmpty) "compile & run" else "cause a compile error")
    def op(testFun: => Any) = if (ignored) { ignore should description in testFun }
    else { it should description in testFun }

    op {
      withTempFile(loadTest("/test-cases/" + testName, imports)) { path =>
        val driver = Driver()
        val testReporter = new TestReporter
        val reporter = testReporter
        val classpath = Array("-classpath", Properties.currentClasspath)

        driver.process(classpath :+ path.toString, reporter)

        val infos = testReporter.storedInfos

        def verifyInfo(level: Int, expected: List[String]) = {
          val actual = infos.filter(_.level == level).map(_.message)

          if (expected.size > 0) {
            val info = actual.mkString("\n")

            expected.foreach(part => info should include(part))
          } else {
            actual shouldBe empty
          }
        }

        verifyInfo(1, expectedWarningsFragments)
        verifyInfo(2, expectedFailures)
      }
    }
  }

  private def loadTest(name: String, imports: String) = wrapInMainObject(
    imports + resolveDirectives(loadResource(name))
  )

  private def wrapInMainObject(source: String) = s"object Main {\n  ${source.linesIterator.mkString("\n  ")}\n}"

}

import dotty.tools.dotc.reporting.StoreReporter

class TestReporter extends StoreReporter(null) {
  def storedInfos = if infos == null then List.empty else infos.toList
}
