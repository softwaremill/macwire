package com.softwaremill.macwire

import cats.effect._

package object catseffectsupport {
    def wireResourceRec[A]: Resource[IO, A] = macro MacwireCatsEffectMacros.wireResourceRec_impl[A]
}