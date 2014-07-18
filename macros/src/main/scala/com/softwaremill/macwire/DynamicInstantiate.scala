package com.softwaremill.macwire

class DynamicInstantiate(instanceLookup: InstanceLookup) {
  def instantiate(className: String): Any = instantiate(loadClass(className))

  private def loadClass(className: String) = {
    Thread.currentThread().getContextClassLoader.loadClass(className)
  }

  def instantiate[T](cls: Class[T]): T = {
    val ctor = cls.getConstructors.apply(0)
    val params = ctor.getParameterTypes.map { paramCls =>
      instanceLookup.lookup(paramCls) match {
        case Nil => throw new InstantiationException(s"Cannot instantiate ${cls.getName}, " +
          s"dependency of type ${paramCls.getName} cannot be found")
        case inst :: Nil => inst.asInstanceOf[AnyRef]
        case insts => throw new InstantiationException(s"Cannot instantiate ${cls.getName}, " +
          s"multiple dependencies of type ${paramCls.getName} found: ${insts.map(_.getClass.getName)}")
      }
    }

    ctor.newInstance(params: _*).asInstanceOf[T]
  }
}
