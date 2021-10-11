package com.softwaremill.macwire

import cats.effect._

package object catseffectsupport {
    def wireApp[T](dependencies: Any*): Resource[IO, T] = macro MacwireCatsEffectMacros.wireApp_impl[T]
}