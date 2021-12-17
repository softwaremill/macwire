package com.softwaremill.macwire

import cats.effect._

package object autocats {
    def autowire[T](dependencies: Any*): Resource[IO, T] = macro MacwireAutoCatsMacros.autowire_impl[T]
}
