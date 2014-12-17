package com.softwaremill

package object macwire extends Tagging {
  private[macwire] type InstanceFactoryMap = Map[Class[_], () => AnyRef]
}
