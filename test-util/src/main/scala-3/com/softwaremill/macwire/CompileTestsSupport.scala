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

import dotty.tools.dotc.reporting.ThrowingReporter
import dotty.tools.dotc.Driver
import java.io.IOException

trait CompileTestsSupport extends BaseCompileTestsSupport with OptionValues {
   type ExpectedFailures = List[String]

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
   
   override def addTest(testName: String, expectedFailures: ExpectedFailures, expectedWarningsFragments: List[String], imports: String = GlobalImports) = {
     testName should (if (expectedFailures.isEmpty) "compile & run" else "cause a compile error") in {
      if expectedWarningsFragments.nonEmpty then throw new NotImplementedError
        withTempFile(loadTest("/test-cases/" + testName, imports)) { path =>
          val driver = Driver()
          val testReporter = new TestReporter
          val reporter = testReporter
          val classpath = Array("-classpath", Properties.currentClasspath)
          
          driver.process(classpath :+ path.toString, reporter)

          val infos = testReporter.storedInfos
          val errors = infos.filter(_.level >= 2).map(_.message)
          if (expectedFailures.size > 0) {
            val error = errors.mkString("\n")
            
            expectedFailures.foreach(part => error should include (part))
          } else {
            errors shouldBe empty
          }
        }      
     }
   }

   private def loadTest(name: String, imports: String) = wrapInMainObject(imports + resolveDirectives(loadResource(name)))

   private def wrapInMainObject(source: String) = s"object Main {\n $source  \n}"

 }

import dotty.tools.dotc.reporting.StoreReporter

class TestReporter extends StoreReporter(null) {
  def storedInfos = if infos == null then List.empty else infos.toList
}