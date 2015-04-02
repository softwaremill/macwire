package com.softwaremill

package object macwire extends Tagging with Macwire {
  private[macwire] type InstanceFactoryMap = Map[Class[_], () => AnyRef]
}
