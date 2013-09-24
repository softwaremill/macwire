package com.softwaremill.macwire.aop

import scala.reflect.ClassTag
import java.lang.reflect.Method
import com.softwaremill.macwire.proxy.ProxyCreator
import javassist.util.proxy.MethodHandler

trait ProxyingInterceptor extends Interceptor {

  def apply[T <: AnyRef](intercepted: T)(implicit tag: ClassTag[T]) = {
    val methodHandler = new MethodHandler {
      def invoke(self: AnyRef, thisMethod: Method, _proceed: Method, args: Array[AnyRef]) = {
        val invocationContext = new InvocationContext {
          def parameters = args
          def target = intercepted
          def method = thisMethod

          def proceedWithParameters(parameters: Array[AnyRef]) = thisMethod.invoke(intercepted, parameters: _*)
        }

        handle(invocationContext)
      }
    }

    ProxyCreator.create(tag, methodHandler)
  }

  def handle(ctx: InvocationContext): AnyRef
}

object ProxyingInterceptor {
  def apply(handler: InvocationContext => AnyRef) = new ProxyingInterceptor {
    def handle(ctx: InvocationContext) = handler(ctx)
  }
}
