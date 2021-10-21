package com.softwaremill.macwire.aop

import scala.reflect.ClassTag

trait Interceptor {

  /** Intercept calls to methods of the `intercepted` instance using this interceptor.
    */
  def apply[T <: AnyRef](intercepted: T)(implicit tag: ClassTag[T]): T
}
