package com.softwaremill.macwire

private[macwire] trait DynamicInstantiate {
  def wireClassInstanceByName(className: String): Any = wireClassInstance(loadClass(className))

  private def loadClass(className: String) = {
    Thread.currentThread().getContextClassLoader.loadClass(className)
  }

  @throws(classOf[InstantiationException])
  def wireClassInstance[T](cls: Class[T]): T = {
    val ctor = cls.getConstructors.apply(0)
    val params = ctor.getParameterTypes.map { paramCls =>
      lookup(paramCls) match {
        case Nil =>
          throw new InstantiationException(
            s"Cannot instantiate ${cls.getName}, " +
              s"dependency of type ${paramCls.getName} cannot be found"
          )
        case inst :: Nil => inst.asInstanceOf[AnyRef]
        case insts       =>
          throw new InstantiationException(
            s"Cannot instantiate ${cls.getName}, " +
              s"multiple dependencies of type ${paramCls.getName} found: ${insts.map(_.getClass.getName)}"
          )
      }
    }

    ctor.newInstance(params: _*).asInstanceOf[T]
  }

  protected def lookup[T](cls: Class[T]): List[T]
}
