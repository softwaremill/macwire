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
    report.errorAndAbort(s"$msg\nWiring path: $showPath")

  private def showPath: String = path.map(showTypeName).mkString(" -> ")
