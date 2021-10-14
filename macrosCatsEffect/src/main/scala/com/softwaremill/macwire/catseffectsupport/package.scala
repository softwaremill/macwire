package com.softwaremill.macwire

import cats.effect._

package object catseffectsupport {
    def autowire[T](dependencies: Any*): Resource[IO, T] = macro MacwireCatsEffectMacros.autowire_impl[T]
}