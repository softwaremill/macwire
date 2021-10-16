package com.softwaremill.macwire.auto

import cats.effect._

package object catssupport {
    def autowire[T](dependencies: Any*): Resource[IO, T] = macro MacwireCatsEffectMacros.autowire_impl[T]
}