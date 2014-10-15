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
    ("simpleValsMultipleParameterLists", Nil),
    ("simpleValsImplicitParameterLists", Nil),
    ("classesWithTraitsLazyValsOkInTrait", Nil),
    ("inheritanceSimpleLazyValsOkInTraits", Nil),
    ("inheritanceSimpleDefsOkInTraits", Nil),
    ("inheritanceTwoLevelSimpleLazyValsOkInTraits", Nil),
    ("inheritanceDoubleSimpleLazyValsOkInTraits", Nil),
    ("inheritanceClassesWithTraitsLazyValsOkInTraits", Nil),
    ("simpleWithAbstractScopeOk", Nil),
    ("methodSingleParamOk", Nil),
    ("methodParamsOk", Nil),
    ("methodMixedOk", Nil),
    ("methodByNameIfMultipleOk", Nil),
    ("methodByNamePrimitiveIfMultipleOk", Nil),
    ("wiredSimple", Nil),
    ("wiredLazy", Nil),
    ("wiredWithWire", Nil),
    ("wiredInherited", Nil),
    ("wiredDefs", Nil),
    ("wiredFromClass", Nil),
    ("wiredClassWithTypeParameters", Nil),
    // explicit param should not be resolved with implicit value when dependency cannot be found during plain, old regular lookup
    ("explicitDepsNotWiredWithImplicitVals", List("Cannot find a value of type", "A")),
    // non-implicit params should be resolved with implicit values if are in scope
    ("explicitDepsWiredWithImplicitValsFromMethodScope", Nil),
    ("explicitDepsWiredWithImplicitValsFromEnclosingModuleScope", Nil),
    ("explicitDepsWiredWithImplicitValsFromParentsScope", Nil),
    // implicit params should be resolved with implicit values
    ("implicitDepsWiredWithImplicitVals", Nil),
    ("implicitDepsWiredWithImplicitValsFromMethodScope", Nil),
    ("implicitDepsWiredWithImplicitValsFromEnclosingModuleScope", Nil),
    ("implicitDepsWiredWithImplicitValsFromParentsScope", Nil),
    // implicit params should be resolved with regular values
    ("implicitDepsWiredWithExplicitVals", Nil),
    ("implicitDepsWiredWithExplicitValsFromEnclosingModuleScope", Nil),
    ("implicitDepsWiredWithExplicitValsFromParentsScope", Nil),
    // dependency resolution should abort compilation when there are ambiguous dependencies in scope
    ("implicitDepsNotWiredWithExplicitAndImplicitValsInEnclosingClassScope", List("Found multiple values of type [Dependency]", "regularDependency", "implicitDependency")),
    ("implicitDepsNotWiredWithExplicitAndImplicitValsInParentsScope", List("Found multiple values of type [Dependency]", "regularDependency", "implicitDependency")),
    ("implicitDepsNotWiredWithoutAnyValsInScope", List("Cannot find a value of type", "Dependency"))
  )

  for ((testName, expectedErrors) <- tests)
    addTest(testName, expectedErrors)

  addTest("simpleValsOkInTraitExtendingMacwire", Nil, "/* Note no additional import needed */")

  def addTest(testName: String, expectedErrors: List[String], imports: String = GlobalImports) {
    testName should (if (expectedErrors == Nil) "compile & run" else "cause a compile error") in {
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
