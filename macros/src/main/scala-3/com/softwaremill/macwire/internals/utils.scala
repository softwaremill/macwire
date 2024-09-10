package com.softwaremill.macwire.internals

import scala.quoted.*

def showTypeName[T: Type](using q: Quotes): String = showTypeName(using q)(q.reflect.TypeRepr.of[T])
def showTypeName(using q: Quotes)(t: q.reflect.TypeRepr): String = t.typeSymbol.name
