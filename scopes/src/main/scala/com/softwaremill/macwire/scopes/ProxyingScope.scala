package com.softwaremill.macwire.scopes

import java.util.UUID
import javassist.util.proxy.{ProxyObject, MethodHandler, ProxyFactory}
import java.lang.reflect.Method
import scala.reflect.ClassTag

trait ProxyingScope extends Scope {
  def apply[T](createT: => T)(implicit tag: ClassTag[T]): T = {
    val key = UUID.randomUUID().toString
    val cls = tag.runtimeClass
    val proxyFactory = new ProxyFactory()
    proxyFactory.setSuperclass(cls)
    val proxiedClass = proxyFactory.createClass()
    val methodHandler = new MethodHandler() {
      def invoke(self: Any, thisMethod: Method, proceed: Method, args: Array[AnyRef]) = {
        val instance = get(key, createT)
        thisMethod.invoke(instance, args: _*)
      }
    }

    // The proxy is a subclass, and must call some super-constructor. For now we try to invoke the no-arg constructor,
    // and if this is not possible, we invoke any constructor with default values for arguments.
    // Consider using Unsafe:
    // https://community.jboss.org/blogs/stuartdouglas/2010/10/12/weld-cdi-and-proxies
    val constructor = findBestConstructor(proxiedClass)
    val instance = constructor.newInstance(constructor.getParameterTypes.map(getDefaultValueForClass(_)): _*)

    instance.asInstanceOf[ProxyObject].setHandler(methodHandler)
    instance.asInstanceOf[T]
  }

  private def findBestConstructor(cls: Class[_]) = {
    val ctors = cls.getConstructors
    ctors.find(_.getParameterTypes.size == 0).getOrElse(ctors.head)
  }

  private val TypeDefaults = Map[Class[_], AnyRef](
    java.lang.Byte.TYPE     -> java.lang.Byte.valueOf(0.toByte),
    java.lang.Short.TYPE    -> java.lang.Short.valueOf(0.toShort),
    java.lang.Integer.TYPE  -> java.lang.Integer.valueOf(0),
    java.lang.Float.TYPE    -> java.lang.Float.valueOf(0),
    java.lang.Double.TYPE   -> java.lang.Double.valueOf(0),
    java.lang.Boolean.TYPE  -> java.lang.Boolean.FALSE)

  private def getDefaultValueForClass(cls: Class[_]): AnyRef = TypeDefaults.getOrElse(cls, null)
}
