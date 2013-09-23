package com.softwaremill.macwire.aop

import java.lang.reflect.Method

trait InvocationContext {
  def method: Method
  def parameters: Array[AnyRef]
  def target: AnyRef

  def proceed(): AnyRef = proceedWithParameters(parameters)
  def proceedWithParameters(parameters: Array[AnyRef]): AnyRef
}
