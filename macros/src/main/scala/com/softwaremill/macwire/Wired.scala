package com.softwaremill.macwire

import scala.reflect.ClassTag

/**
 * Dynamically access wired instances in a module
 */
class Wired(protected val instanceFactoryMap: InstanceFactoryMap) extends InstanceLookup with DynamicInstantiate {
  def withInstances(wiredInstanced: AnyRef*): Wired = {
    new Wired(instanceFactoryMap ++ wiredInstanced.map(wi => wi.getClass -> (() => wi)))
  }
  
  def withInstanceFactory[T](wiredInstanceFactory: () => T)(implicit ct: ClassTag[T]): Wired = {
    new Wired(instanceFactoryMap + (ct.runtimeClass -> wiredInstanceFactory.asInstanceOf[() => AnyRef]))
  }
}

object Wired {
  def apply(implsByClass: InstanceFactoryMap) = new Wired(implsByClass)
}