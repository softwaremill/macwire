package com.softwaremill.macwire

import MacwireMacros.ImplsMap

class ImplLookup(implsByClass: ImplsMap) {
  private val lookupMap = prepareMap

  def lookup[T](cls: Class[T]): List[T] = {
    lookupMap.getOrElse(cls, Nil).map(_().asInstanceOf[T])
  }

  def lookupSingleOrThrow[T](cls: Class[T]) = lookup(cls) match {
    case Nil => throw new RuntimeException(s"Cannot find implementations of class $cls!")
    case List(i) => i
    case l => throw new RuntimeException(s"Found multiple implementations of class $cls: $l!")
  }

  private def prepareMap: Map[Class[_], List[() => AnyRef]] = {
    implsByClass
      .toList
      .flatMap { case (startingCls, impl) =>
      def allSuperClasses(cls: Class[_]): List[Class[_]] = {
        if (cls == null) {
          Nil
        } else {
          (cls :: allSuperClasses(cls.getSuperclass)) ++ cls.getInterfaces.flatMap(allSuperClasses)
        }
      }

      allSuperClasses(startingCls).map(_ -> impl)
    }.groupBy(_._1)
    .map { case (cls, clsAndImpls) => cls -> clsAndImpls.map(_._2) }
    .toMap
  }
}

object ImplLookup {
  def apply(implsByClass: ImplsMap) = new ImplLookup(implsByClass)
}
