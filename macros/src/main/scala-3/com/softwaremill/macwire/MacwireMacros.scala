package com.softwaremill.macwire

import com.softwaremill.macwire.internals.*
import scala.quoted.*

object MacwireMacros {
  private val log = new Logger()

  def wireImpl[T: Type](using q: Quotes): Expr[T] = {
    import q.reflect.*

    val constructorCrimper = new ConstructorCrimper[q.type, T](log)
    val companionCrimper = new CompanionCrimper[q.type, T](log)

    lazy val targetType = constructorCrimper.targetType.toString
    //TODO
    lazy val whatWasWrong: String = "???"
    //  {
    //   if (constructorCrimper.constructor.isEmpty && companionCrimper.companionType.isEmpty)
    //     s"Cannot find a public constructor nor a companion object for [$targetType]"
    //   else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.isEmpty)
    //     s"Companion object for [$targetType] has no apply methods constructing target type."
    //   else if (companionCrimper.applies.isDefined && companionCrimper.applies.get.size > 1)
    //     s"No public primary constructor found for $targetType and multiple matching apply methods in its companion object were found."
    //   else s"Target type not supported for wiring: $targetType. Please file a bug report with your use-case."
    // }

    val code: Tree = (constructorCrimper.constructorTree orElse companionCrimper.applyTree)
      .getOrElse(report.throwError(whatWasWrong))
      
    log(s"Generated code: ${code.show}, ${code}")
    code.asExprOf[T]
  }

}
