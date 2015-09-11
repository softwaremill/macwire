package com.softwaremill.macwire

import java.io.File

import org.scalatest.{Matchers, FlatSpec}
import io.Source
import tools.reflect.ToolBoxError

class CompileTests extends FlatSpec with Matchers {

  val GlobalImports = "import com.softwaremill.macwire._\n\n"
  val DirectiveRegexp = "#include ([a-zA-Z]+)".r
  val EmptyResult = "\n\n()"

  def ambiguousResMsg(depClassName: String): String = s"Found multiple values of type [$depClassName]"
  def valueNotFound(depClassName: String): String = s"Cannot find a value of type: [$depClassName]"
  
  type ExpectedFailures = List[String]
  
  val expectedFailures = List(
    "explicitDepsNotWiredWithImplicitVals" -> List(valueNotFound("A")),
    "explicitDepsWiredWithImplicitValsFromMethodScope" -> List(ambiguousResMsg("A"), "dependency", "implicitDependencyA"),
    "importAmbiguous" -> List(ambiguousResMsg("A"), "myA", "theA"),
    "methodWithTaggedParamsNotFound" -> List(valueNotFound("com.softwaremill.macwire.tagging.@@[Berry,Blue]")),
    "methodWithTaggedParamsAmbiguous" -> List(ambiguousResMsg("com.softwaremill.macwire.tagging.@@[Berry,Blue]"), "blueberryArg1", "blueberryArg2"),
    "multipleMethodParameters" -> List(ambiguousResMsg("A"), "a1", "a2"),
    "nestedMethodsWired" -> List(ambiguousResMsg("A"), "outerA", "innerA"),
    "nestedWithManyMatchingParamsWired" -> List(ambiguousResMsg("A"), "a1", "a2", "a3"),
    "simpleValsMissingValue" -> List(valueNotFound("B")),
    "simpleValsDuplicateValue" -> List(ambiguousResMsg("B"), "theB1", "theB2"),
    "taggedNoValueWithTag" -> List(valueNotFound("com.softwaremill.macwire.tagging.@@[Berry,Blue]"))
  ).map{ case (n,errs) => (n + ".failure", errs)}

  val expectedFailuresMap = expectedFailures.toMap
  
  checkNoDuplicatedExpectedFailure()

  val testCaseNames = findTestCaseFiles(basedOn = "import.success").map(_.getName).sorted
  val (successNames,failureNames) = partitionSuccessAndFailures(testCaseNames)

  checkEachExpectedFailureMatchAFailureTestCase(expectedFailures, failureNames)

  // add the tests
  successNames.foreach(addTest(_, Nil))
  failureNames.foreach(name => addTest(name, expectedFailuresMap.getOrElse(name,
    sys.error(s"Cannot find expected failures for $name")) ))

  def checkNoDuplicatedExpectedFailure(): Unit = {
    if (expectedFailures.size > expectedFailuresMap.size) {
      val duplicates = expectedFailures.map(_._1).diff(expectedFailuresMap.keySet.toList)
      sys.error("You have duplicated expected failures:\n- " + duplicates.mkString("\n- "))
    }
  }
  
  def checkEachExpectedFailureMatchAFailureTestCase(expectedFailures: List[(String, ExpectedFailures)], testCaseNames: List[String]): Unit = {
    val missingFileNames = expectedFailures.map(_._1).filter { name => !testCaseNames.contains(name) }
    if( missingFileNames.nonEmpty ) {
      sys.error("You have defined expected failures that are not matched by a '.failure' test-case file:\n- " +
        missingFileNames.mkString("\n- "))
    }
  }

  def partitionSuccessAndFailures(names: List[String]): (List[String], List[String]) = {
    val res@(_, failureNames) = names.partition(_.endsWith(".success"))
    failureNames.filterNot(_.endsWith(".failure")) match {
      case Nil => ()
      case neitherSuccessNorFailure => sys.error("Test case files must either end with .success or .failure:\n- " +
        neitherSuccessNorFailure.mkString("\n- "))
    }
    res
  }

  def addTest(testName: String, expectedFailures: ExpectedFailures, imports: String = GlobalImports) {
    testName should (if (expectedFailures.isEmpty) "compile & run" else "cause a compile error") in {
      import scala.reflect.runtime._
      val cm = universe.runtimeMirror(getClass.getClassLoader)

      import scala.tools.reflect.ToolBox
      val tb = cm.mkToolBox()

      val source = loadTest("/test-cases/" + testName, imports)

      try {
        tb.eval(tb.parse(source))
        if (expectedFailures.nonEmpty) {
          fail(s"Expected the following compile errors: $expectedFailures")
        }
      } catch {
        case e: ToolBoxError => {
          if (expectedFailures.isEmpty) {
            fail(s"Expected compilation & evaluation to be successful, but got an error: ${e.message}", e)
          } else {
            expectedFailures.foreach(expectedError => e.message should include (expectedError))
          }
        }
      }
    }
  }

  def findTestCaseFiles(basedOn: String): List[File] = {
    val resource = this.getClass.getResource("/test-cases/" + basedOn)
    val file = new File(resource.toURI)
    file.getParentFile.listFiles().toList.filter(_.isFile) match {
      case Nil => sys.error(s"No test found, make sure /test-cases/$basedOn exists in your classpath.")
      case testCaseFiles => testCaseFiles
    }
  }

  def loadTest(name: String, imports: String) = imports + resolveDirectives(loadResource(name)) + EmptyResult

  def loadResource(name: String) = {
    val resource = this.getClass.getResourceAsStream(name)
    if (resource == null) throw new IllegalArgumentException(s"Cannot find resource: $name")
    Source.fromInputStream(resource).getLines().mkString("\n")
  }

  def resolveDirectives(in: String): String = {
    DirectiveRegexp.findAllMatchIn(in).foldLeft(in)((acc, m) => {
      val includeName = m.group(1)
      val replacement = loadResource("/include/" + includeName)
      acc.replaceAll("#include " + includeName + "(?!\\w)", replacement)
    })
  }
}
