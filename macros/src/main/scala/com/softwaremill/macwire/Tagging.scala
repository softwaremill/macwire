package com.softwaremill.macwire

// inspired by https://gist.github.com/milessabin/89c9b47a91017973a35f
trait Tagging {
  type Tag[+U] = { type Tag <: U }
  type @@[T, +U] = T with Tag[U]
  type Tagged[T, +U] = T with Tag[U]
  implicit class Tagger[T](t: T) {
    def taggedWith[U]: T @@ U = t.asInstanceOf[T @@ U]
  }
  implicit class AndTagger[T, U](t: T @@ U) {
    def andTaggedWith[V]: T @@ (U with V) = t.asInstanceOf[T @@ (U with V)]
  }
}

object Tagging extends Tagging
