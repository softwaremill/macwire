package com.softwaremill.macwire.scopes

import scala.reflect.ClassTag

object NoOpScope extends Scope {
  def apply[T](createT: => T)(implicit tag: ClassTag[T]) = createT
  def get[T](key: String, createT: => T) = throw new RuntimeException("Should never be called!")
}
