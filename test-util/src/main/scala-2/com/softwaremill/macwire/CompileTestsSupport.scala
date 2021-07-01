package com.softwaremill.macwire

import scala.tools.reflect.ToolBoxError

trait CompileTestsSupport extends BaseCompileTestsSupport {
  
   def addTest(testName: String, expectedFailures: ExpectedFailures, expectedWarningsFragments: List[String], imports: String = GlobalImports) = {
     testName should (if (expectedFailures.isEmpty) "compile & run" else "cause a compile error") in {
       import scala.reflect.runtime.universe
       val cm = universe.runtimeMirror(getClass.getClassLoader)

       import scala.tools.reflect.ToolBox
       val tb = cm.mkToolBox()

       val source = loadTest("/test-cases/" + testName, imports)

       try {
         tb.eval(tb.parse(source))
         if (expectedFailures.nonEmpty) {
           fail(s"Expected the following compile errors: $expectedFailures")
         }
         val warnings = tb.frontEnd.infos.filter(_.severity == tb.frontEnd.WARNING).map(_.msg).toList
         lazy val warningsString = "\n - " + warnings.mkString("\n - ")
         (warnings, expectedWarningsFragments) match {
           case (Nil, Nil)  => () // ok
           case (_,   Nil)  => fail(s"Expected compilation to have no warning, but got:" + warningsString )
           case (Nil, _)    => fail(s"Expected the following compile warnings fragments: $expectedWarningsFragments")
           case (one :: Nil, _)    => expectedWarningsFragments.foreach(expectedWarning => one should include (expectedWarning))
           case (_, _) => fail(s"More than one warning found:" + warningsString)
         }
         if (tb.frontEnd.hasWarnings) {

           if (expectedWarningsFragments.isEmpty) {
             fail(s"Expected compilation to have no warning, but got:\n - " + warnings.mkString("\n - ") )
           } else {
           }
         } else if (expectedWarningsFragments.nonEmpty) {
           fail(s"Expected the following compile warnings: $expectedWarningsFragments")
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

   private def loadTest(name: String, imports: String) = imports + resolveDirectives(loadResource(name)) + EmptyResult

 }
