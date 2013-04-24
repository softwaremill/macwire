package com.softwaremill.macwire.two_modules_and_scope

trait OtherModule extends SomeModule {
  val c = wire[B]
}
