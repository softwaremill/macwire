package com.softwaremill.macwire

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
// import scala.tools.reflect.ToolBoxError

trait CompileTestsSupport extends AnyFlatSpec with Matchers {
   type ExpectedFailures = List[String]

   val GlobalImports = "import com.softwaremill.macwire._\n\n"
   val DirectiveRegexp = "#include ([a-zA-Z]+)".r
   val EmptyResult = "\n\n()"

   def ambiguousResMsg(depClassName: String): String = s"Found multiple values of type [$depClassName]"
   def valueNotFound(depClassName: String): String = s"Cannot find a value of type: [$depClassName]"

   def runTestsWith(expectedFailures: List[(String,ExpectedFailures)], expectedWarnings: List[(String, List[String])] = Nil): Unit = {
     val expectedFailuresMap = expectedFailures.toMap
     checkNoDuplicatedExpectation(expectedFailures, expectedFailuresMap)

     val expectedWarningsMap = expectedWarnings.toMap
     checkNoDuplicatedExpectation(expectedWarnings, expectedWarningsMap)

     val firstFailureTestCase = expectedFailures.headOption.map(_._1).getOrElse(
       sys.error("At least one failure expected -- change this code otherwise")) + ".failure"
     val testCaseNames = findTestCaseFiles(basedOn = firstFailureTestCase).map(_.getName).sorted
     val (successNames,failureNames,warningNames) = partitionSuccessFailureWarnings(testCaseNames)

     checkEachExpectationMatchATestCase(expectedFailures, failureNames)
     checkEachExpectationMatchATestCase(expectedWarnings, warningNames)

     // add the tests
     successNames.foreach(name => addTest(name + ".success", Nil, Nil))
     //TODO add support for warning tests
    //  warningNames.foreach(name => addTest(name + ".warning", Nil, expectedWarningsMap.getOrElse(name,
    //    sys.error(s"Cannot find expected warning for $name"))))
     failureNames.foreach(name => addTest(name + ".failure", expectedFailuresMap.getOrElse(name,
       sys.error(s"Cannot find expected failures for $name")), Nil ))
   }

   private def checkNoDuplicatedExpectation(expectationList: List[(String, ExpectedFailures)],
                                            expectationMap: Map[String, ExpectedFailures]): Unit = {
     if (expectationList.size > expectationMap.size) {
       val duplicates = expectationList.map(_._1).diff(expectationMap.keySet.toList)
       sys.error("You have duplicated expectation:\n- " + duplicates.mkString("\n- "))
     }
   }

   private def checkEachExpectationMatchATestCase(expectations: List[(String, ExpectedFailures)], testCaseNames: List[String]): Unit = {
     val missingFileNames = expectations.map(_._1).filter { name => !testCaseNames.contains(name) }
     if( missingFileNames.nonEmpty ) {
       sys.error("You have defined expectations that are not matched by a test-case file:\n- " +
         missingFileNames.mkString("\n- "))
     }
   }

   /** @return (successes, failures, warnings) stripped of their suffix */
   private def partitionSuccessFailureWarnings(names: List[String]): (List[String], List[String], List[String]) = {
     val (successNames, nonSuccess) = names.partition(_.endsWith(".success"))
     val (warningNames, failureNames) = nonSuccess.partition(_.endsWith(".warning"))
     failureNames.filterNot(_.endsWith(".failure")) match {
       case Nil => ()
       case neitherSuccessNorFailure => sys.error("Test case files must either end with .success, .failure or .warning:\n- " +
         neitherSuccessNorFailure.mkString("\n- "))
     }

     (successNames.map(_.stripSuffix(".success")),
       failureNames.map(_.stripSuffix(".failure")),
        warningNames.map(_.stripSuffix(".warning")))
   }
    val tempPath = Paths.get(Properties.tempTestFile)

   private def createTempFile(content: String): Path = {
    // Files.delete(tempPath) 
    Files.write(tempPath, content.getBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
  }


   def addTest(testName: String, expectedFailures: ExpectedFailures, expectedWarningsFragments: List[String], imports: String = GlobalImports) = {
     testName should (if (expectedFailures.isEmpty) "compile & run" else "cause a compile error") in {
      if expectedWarningsFragments.nonEmpty then throw new NotImplementedError

      import dotty.tools.dotc.Driver

       val source = loadTest("/test-cases/" + testName, imports)
       val testFile = createTempFile(source)
       val driver = Driver()
       val reporter = new TestReporter()
       val classpath = Array("-classpath", Properties.currentClasspath)
      driver.process(classpath :+ tempPath.toString, reporter)

      val infos = reporter.storedInfos
      infos.map(_.msg) should contain theSameElementsAs expectedFailures
     }
   }

   private def findTestCaseFiles(basedOn: String): List[File] = {
     val resource = this.getClass.getResource("/test-cases/" + basedOn)
     if (resource == null) {
       sys.error(s"No test found, make sure /test-cases/$basedOn exists in your classpath.")
     }
     val file = new File(resource.toURI)
     file.getParentFile.listFiles().toList.filter(_.isFile) match {
       case Nil => sys.error(s"No test found, make sure /test-cases/$basedOn exists in your classpath.")
       case testCaseFiles => testCaseFiles
     }
   }

   private def loadTest(name: String, imports: String) = imports + resolveDirectives(loadResource(name))

   private def loadResource(name: String) = {
     val resource = this.getClass.getResourceAsStream(name)
     if (resource == null) throw new IllegalArgumentException(s"Cannot find resource: $name")
     Source.fromInputStream(resource).getLines().mkString("\n")
   }

   private def resolveDirectives(in: String): String = {
     DirectiveRegexp.findAllMatchIn(in).foldLeft(in)((acc, m) => {
       val includeName = m.group(1)
       val replacement = loadResource("/include/" + includeName)
       acc.replaceAll("#include " + includeName + "(?!\\w)", replacement)
     })
   }
 }

import dotty.tools.dotc.reporting.StoreReporter

class TestReporter extends StoreReporter(null) {
  def storedInfos = infos.toList
}