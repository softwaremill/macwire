package com.softwaremill.macwire

import cats.effect._

package object autocats {
    def autowire[T](dependencies: Any*): Resource[IO, T] = macro MacwireCatsEffectMacros.autowire2_impl[T]
}
