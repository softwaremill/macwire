package com.softwaremill

import scala.language.experimental.macros

package object macwire {
  def wire[T]: T = macro MacwireMacros.wire_impl[T]
  def wireSet[T]: Set[T] = macro MacwireMacros.wireSet_impl[T]
  def wireImplicit[T]: T = macro MacwireMacros.wireImplicit_impl[T]
  def wiredInModule(in: AnyRef): Wired = macro MacwireMacros.wiredInModule_impl
}
