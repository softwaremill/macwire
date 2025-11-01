package com.softwaremill.macwire

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

private[macwire] trait BaseCompileTestsSupport extends AnyFlatSpec with Matchers {
  type ExpectedFailures = List[String]

  protected val testsSubdirectory: Option[String] = None

  val GlobalImports = "import com.softwaremill.macwire._\n\n"
  val DirectiveRegexp = "#include ([a-zA-Z]+)".r
  val EmptyResult = "\n\n()"

  def ambiguousResMsg(depClassName: String): String = s"Found multiple values of type [$depClassName]"
  def valueNotFound(depClassName: String): String = s"Cannot find a value of type: [$depClassName]"

  def runTestsWith(
      expectedFailures: List[(String, ExpectedFailures)],
      expectedWarnings: List[(String, List[String])] = Nil
  ): Unit = {
    val expectedFailuresMap = expectedFailures.toMap
    checkNoDuplicatedExpectation(expectedFailures, expectedFailuresMap)

    val expectedWarningsMap = expectedWarnings.toMap
    checkNoDuplicatedExpectation(expectedWarnings, expectedWarningsMap)

    val firstFailureTestCase = expectedFailures.headOption
      .map(_._1)
      .getOrElse(sys.error("At least one failure expected -- change this code otherwise")) + ".failure"
    val testCaseNames = findTestCaseFiles(basedOn = firstFailureTestCase).map(_.getName).sorted
    val (successNames, failureNames, warningNames) = partitionSuccessFailureWarnings(testCaseNames)

    checkEachExpectationMatchATestCase(expectedFailures, failureNames)
    checkEachExpectationMatchATestCase(expectedWarnings, warningNames)

    // add the tests
    successNames.map(isIgnoredWithName).foreach { case (name, ignored) =>
      addTest(name + ".success", ignored, Nil, Nil)
    }
    warningNames.map(isIgnoredWithName).foreach { case (name, ignored) =>
      addTest(
        name + ".warning",
        ignored,
        Nil,
        if (ignored) Nil
        else findOrElseFail(expectedWarningsMap, name, s"Cannot find expected warning for $name")
      )
    }
    failureNames.map(isIgnoredWithName).foreach { case (name, ignored) =>
      addTest(
        name + ".failure",
        ignored,
        if (ignored) Nil else findOrElseFail(expectedFailuresMap, name, s"Cannot find expected failures for $name"),
        Nil
      )
    }
  }

  def findOrElseFail(m: Map[String, List[String]], testName: String, errMsg: String) =
    m.find { case (name, _) => testName.startsWith(name) }.getOrElse(sys.error(errMsg))._2

  private def checkNoDuplicatedExpectation(
      expectationList: List[(String, ExpectedFailures)],
      expectationMap: Map[String, ExpectedFailures]
  ): Unit = {
    if (expectationList.size > expectationMap.size) {
      val duplicates = expectationList.map(_._1).diff(expectationMap.keySet.toList)
      sys.error("You have duplicated expectation:\n- " + duplicates.mkString("\n- "))
    }
  }

  private def checkEachExpectationMatchATestCase(
      expectations: List[(String, ExpectedFailures)],
      testCaseNames: List[String]
  ): Unit = {
    val missingFileNames = expectations.map(_._1).filter { name => !testCaseNames.exists(_.startsWith(name)) }
    if (missingFileNames.nonEmpty) {
      sys.error(
        "You have defined expectations that are not matched by a test-case file:\n- " +
          missingFileNames.mkString("\n- ")
      )
    }
  }

  /** @return (successes, failures, warnings) stripped of their suffix */
  private def partitionSuccessFailureWarnings(names: List[String]): (List[String], List[String], List[String]) = {
    val (successNames, nonSuccess) = names.partition(_.endsWith(".success"))
    val (warningNames, failureNames) = nonSuccess.partition(_.endsWith(".warning"))
    failureNames.filterNot(_.endsWith(".failure")) match {
      case Nil                      => ()
      case neitherSuccessNorFailure =>
        sys.error(
          "Test case files must either end with .success, .failure or .warning:\n- " +
            neitherSuccessNorFailure.mkString("\n- ")
        )
    }

    (
      successNames.map(_.stripSuffix(".success")),
      failureNames.map(_.stripSuffix(".failure")),
      warningNames.map(_.stripSuffix(".warning"))
    )
  }

  protected val ignoreSuffixes: List[String]

  private def isIgnoredWithName(testCase: String): (String, Boolean) = ignoreSuffixes.find(testCase.endsWith) match {
    case None         => (testCase, false)
    case Some(suffix) => (testCase.stripSuffix(suffix), true)
  }

  def addTest(
      testName: String,
      ignored: Boolean,
      expectedFailures: ExpectedFailures,
      expectedWarningsFragments: List[String],
      imports: String = GlobalImports
  ): Unit

  private def findTestCaseFiles(basedOn: String): List[File] = {
    val resource = this.getClass.getResource(baseDirectory + basedOn)
    if (resource == null) {
      sys.error(s"No test found, make sure $baseDirectory$basedOn exists in your classpath.")
    }
    val file = new File(resource.toURI)
    file.getParentFile.listFiles().toList.filter(_.isFile) match {
      case Nil           => sys.error(s"No test found, make sure /$baseDirectory/$basedOn exists in your classpath.")
      case testCaseFiles => testCaseFiles
    }
  }

  def baseDirectory = "/test-cases/" + testsSubdirectory.fold("")(_ + "/")

  def loadResource(name: String) = {
    val resource = this.getClass.getResourceAsStream(name)
    if (resource == null) throw new IllegalArgumentException(s"Cannot find resource: $name")
    Source.fromInputStream(resource).getLines().mkString("\n")
  }

  def resolveDirectives(in: String): String = {
    DirectiveRegexp
      .findAllMatchIn(in)
      .foldLeft(in)((acc, m) => {
        val includeName = m.group(1)
        val replacement = loadResource("/include/" + includeName)
        acc.replaceAll("#include " + includeName + "(?!\\w)", replacement)
      })
  }
}
