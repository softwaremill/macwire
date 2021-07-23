package com.softwaremill.macwire

import scala.quoted.*

package object internals {
  def showTypeName[T: Type](using q: Quotes): String = showTypeName(using q)(q.reflect.TypeRepr.of[T])
  def showTypeName(using q: Quotes)(t: q.reflect.TypeRepr): String = t.typeSymbol.name
}
