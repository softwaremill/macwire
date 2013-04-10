package com.softwaremill.macwire

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import io.Source
import tools.reflect.ToolBoxError

class CompileTests extends FlatSpec with ShouldMatchers {

  val GlobalImports = "import com.softwaremill.macwire.MacwireMacros._\n\n"
  val DirectiveRegexp = "#include ([a-zA-Z]+)".r
  val EmptyResult = "\n\n()"

  val tests = List(
    ("simpleValsOkInTrait", Nil),
    ("simpleValsOkInObject", Nil),
    ("simpleValsOkInClass", Nil),
    ("simpleValsErrorMissingValue", List("Cannot find a value of type: [B]")),
    ("simpleValsErrorDuplicateValue", List("Found multiple values of type [B]", "theB1", "theB2")),
    ("simpleDefsOkInTrait", Nil),
    ("simpleLazyValsOkInTrait", Nil),
    ("simpleWithAbstractOk", Nil),
    ("simpleValsReferenceWithAscriptionOk", Nil),
    ("simpleLazyValsNotInOrderOk", Nil),
    ("classesWithTraitsLazyValsOkInTrait", Nil),
    ("inheritanceSimpleLazyValsOkInTraits", Nil),
    ("inheritanceSimpleDefsOkInTraits", Nil),
    ("inheritanceTwoLevelSimpleLazyValsOkInTraits", Nil),
    ("inheritanceDoubleSimpleLazyValsOkInTraits", Nil),
    ("inheritanceClassesWithTraitsLazyValsOkInTraits", Nil)
  )

  for ((testName, expectedErrors) <- tests)
    addTest(testName, expectedErrors)

  addTest("simpleValsOkInTraitExtendingMacwire", Nil, "/* Note no additional import needed */")

  def addTest(testName: String, expectedErrors: List[String], imports: String = GlobalImports) {
    it should s"$testName should ${if (expectedErrors == Nil) "compile & run" else "cause a compile error"}" in {
      import scala.reflect.runtime._
      val cm = universe.runtimeMirror(getClass.getClassLoader)

      import scala.tools.reflect.ToolBox
      val tb = cm.mkToolBox()

      val source = loadTest(testName, imports)

      try {
        tb.eval(tb.parse(source))
        if (expectedErrors != Nil) {
          fail(s"Expected the following compile errors: $expectedErrors")
        }
      } catch {
        case e: ToolBoxError => {
          if (expectedErrors == Nil) {
            fail(s"Expected compilation & evaluation to be successful, but got an error: ${e.message}", e)
          } else {
            expectedErrors.foreach(expectedError => e.message should include (expectedError))
          }
        }
      }
    }
  }

  def loadTest(name: String, imports: String) = imports + resolveDirectives(loadResource(name)) + EmptyResult

  def loadResource(name: String) = {
    val resource = this.getClass.getResourceAsStream("/" + name)
    if (resource == null) throw new IllegalArgumentException(s"Cannot find resource: $name")
    Source.fromInputStream(resource).getLines().mkString("\n")
  }

  def resolveDirectives(in: String): String = {
    DirectiveRegexp.findAllMatchIn(in).foldLeft(in)((acc, m) => {
      val includeName = m.group(1)
      val replacement = loadResource(includeName)
      acc.replaceAll("#include " + includeName + "(?!\\w)", replacement)
    })
  }
}
