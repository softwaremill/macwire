package com.softwaremill.macwire.scopes

import scala.reflect.ClassTag

trait Scope {
  def apply[T](t: => T)(implicit tag: ClassTag[T]): T
  def get[T](key: String, createT: => T): T
}
