package com.softwaremill

package object macwire {
  private[macwire] type InstanceFactoryMap = Map[Class[_], () => AnyRef]
}
