package com.softwaremill.macwire.internals.autowire

import scala.quoted.Quotes
import com.softwaremill.macwire.internals.showTypeName

class ReportError[Q <: Quotes](using val q: Q):
  import q.reflect.*

  private var path: Vector[TypeRepr] = Vector.empty

  def withType[T](t: TypeRepr)(thunk: => T): T =
    path = path :+ t
    try thunk
    finally path = path.init

  def apply(msg: String): Nothing =
    val suffix = if path.nonEmpty then s";\nwiring path: $showPath" else ""
    report.errorAndAbort(s"$msg$suffix")

  private def showPath: String = path.map(showTypeName).mkString(" -> ")
