package com.softwaremill.macwire.packages.child

import com.softwaremill.macwire._
import com.softwaremill.macwire.packages.A

trait ChildModule {
  val a = wire[A]
}
