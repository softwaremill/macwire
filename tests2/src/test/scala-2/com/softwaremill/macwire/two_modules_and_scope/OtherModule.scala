package com.softwaremill.macwire.two_modules_and_scope

import com.softwaremill.macwire._

trait OtherModule extends SomeModule {
  val c = wire[B]
}
