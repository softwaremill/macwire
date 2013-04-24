package com.softwaremill.macwire.packages.child

import com.softwaremill.macwire.Macwire
import com.softwaremill.macwire.packages.A

trait ChildModule extends Macwire {
  val a = wire[A]
}
