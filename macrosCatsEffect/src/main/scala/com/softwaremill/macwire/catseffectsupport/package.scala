package com.softwaremill.macwire

import cats.effect._

package object catseffectsupport {
    //hmmm at the beginning we are going to accept here only resources, but we expect to accept here also configs, factory methods, effects and so on. I'd like to express the current state of work with the accepted type as close as possible 
    // Phase 2 - accept Resource[IO, ?] & factory methods
    // Phase 3 - accept Resource[IO, ?] & instances, but factory methods  & constructors 
    def wireResourceRec[T](dependencies: Any*): Resource[IO, T] = macro MacwireCatsEffectMacros.wireResourceRec_impl[T]
}