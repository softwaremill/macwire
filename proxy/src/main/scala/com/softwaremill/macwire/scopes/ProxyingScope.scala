package com.softwaremill.macwire.scopes

import java.util.UUID
import javassist.util.proxy.MethodHandler
import java.lang.reflect.Method
import scala.reflect.ClassTag
import com.softwaremill.macwire.proxy.ProxyCreator

trait ProxyingScope extends Scope {

  def apply[T](createT: => T)(implicit tag: ClassTag[T]): T = {
    val key = UUID.randomUUID().toString
    val methodHandler = new MethodHandler() {
      def invoke(self: Any, thisMethod: Method, proceed: Method, args: Array[AnyRef]) = {
        val instance = get(key, createT)
        thisMethod.invoke(instance, args: _*)
      }
    }

    ProxyCreator.create(tag, methodHandler)
  }
}
