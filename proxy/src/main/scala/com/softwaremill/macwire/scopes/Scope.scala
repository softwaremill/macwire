package com.softwaremill.macwire.scopes

import scala.reflect.ClassTag

trait Scope {
  /**
   * Create a scoped value with the given factory class.
   */
  def apply[T](createT: => T)(implicit tag: ClassTag[T]): T

  /**
   * Get an instance from the current scope, for the specified key, using the given factory class to create an instance
   * if not yet created.
   */
  def get[T](key: String, createT: => T): T
}
