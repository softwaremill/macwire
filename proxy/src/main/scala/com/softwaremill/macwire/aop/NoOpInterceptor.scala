package com.softwaremill.macwire.aop

import scala.reflect.ClassTag

object NoOpInterceptor extends Interceptor {
  def apply[T](intercepted: T)(implicit tag: ClassTag[T]) = intercepted
}
