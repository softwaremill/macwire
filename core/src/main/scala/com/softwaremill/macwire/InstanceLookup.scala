package com.softwaremill.macwire

class InstanceLookup(instancesByClass: Map[Class[_], AnyRef]) {
  private val lookupMap = prepareMap

  def lookup[T](cls: Class[T]): List[T] = {
    lookupMap.getOrElse(cls, Nil).map(_.asInstanceOf[T])
  }

  def lookupSingleOrThrow[T](cls: Class[T]) = lookup(cls) match {
    case Nil => throw new RuntimeException(s"Cannot find instances of class $cls!")
    case List(i) => i
    case l => throw new RuntimeException(s"Found multiple instances of class $cls: $l!")
  }

  private def prepareMap: Map[Class[_], List[AnyRef]] = {
    instancesByClass
      .toList
      .flatMap { case (startingCls, inst) =>
      def allSuperClasses(cls: Class[_]): List[Class[_]] = {
        if (cls == null) {
          Nil
        } else {
          (cls :: allSuperClasses(cls.getSuperclass)) ++ cls.getInterfaces.flatMap(allSuperClasses)
        }
      }

      allSuperClasses(inst.getClass).map(_ -> inst)
    }.groupBy(_._1)
    .map { case (cls, clsAndInsts) => cls -> clsAndInsts.map(_._2) }
    .toMap
  }
}

object InstanceLookup {
  def apply(instancesByClass: Map[Class[_], AnyRef]) = new InstanceLookup(instancesByClass)
}
