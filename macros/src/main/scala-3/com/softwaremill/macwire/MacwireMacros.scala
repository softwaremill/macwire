package com.softwaremill.macwire

import com.softwaremill.macwire.internals.*
import scala.quoted.*

object MacwireMacros {
  private val log = new Logger()

  def wireImpl[T: Type](using q: Quotes): Expr[T] = {
    import q.reflect.*

    val constructorCrimper = new ConstructorCrimper[q.type, T](log)
    val companionCrimper = new CompanionCrimper[q.type, T](log)
    
    lazy val whatWasWrong: String = {
      if (constructorCrimper.constructor.isEmpty && companionCrimper.applies.isDefined && companionCrimper.applies.get.isEmpty)
        s"Cannot find a public constructor and the companion object has no apply methods constructing target type for [${showTypeName[T]}]"
      else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.size > 1)
        s"No public primary constructor found for ${showTypeName[T]} and multiple matching apply methods in its companion object were found."
      else s"Target type not supported for wiring: ${showTypeName[T]}. Please file a bug report with your use-case."
    }

    val code: Tree = (constructorCrimper.constructorTree orElse companionCrimper.applyTree)
      .getOrElse(report.throwError(whatWasWrong))
      
    log(s"Generated code: ${code.show}, ${code}")
    code.asExprOf[T]
  }

}
