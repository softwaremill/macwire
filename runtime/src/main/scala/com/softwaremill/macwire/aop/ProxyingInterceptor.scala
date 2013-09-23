package com.softwaremill.macwire.aop

import scala.reflect.ClassTag
import java.lang.reflect.Method
import com.softwaremill.macwire.proxy.ProxyCreator
import javassist.util.proxy.MethodHandler

class ProxyingInterceptor(invocationHandler: InvocationContext => AnyRef) extends Interceptor {

  def apply[T <: AnyRef](intercepted: T)(implicit tag: ClassTag[T]) = {
    val methodHandler = new MethodHandler {
      def invoke(self: AnyRef, thisMethod: Method, _proceed: Method, args: Array[AnyRef]) = {
        val invocationContext = new InvocationContext {
          def parameters = args
          def target = intercepted
          def method = _proceed

          def proceedWithParameters(parameters: Array[AnyRef]) = thisMethod.invoke(intercepted, parameters: _*)
        }

        invocationHandler(invocationContext)
      }
    }

    ProxyCreator.create(tag, methodHandler)
  }
}

object ProxyingInterceptor {
  def apply(handler: InvocationContext => AnyRef) = new ProxyingInterceptor(handler)
}
