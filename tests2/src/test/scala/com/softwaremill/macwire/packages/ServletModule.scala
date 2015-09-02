package com.softwaremill.macwire.packages

import com.softwaremill.macwire._
import com.softwaremill.macwire.packages.child.ChildModule

trait SubModule extends ChildModule {
  def b = wire[B]
}
