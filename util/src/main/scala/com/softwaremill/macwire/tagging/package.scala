package com.softwaremill.macwire

package object tagging {
  @deprecated("Use com.softwaremill.tagging instead")
  type Tag[+U] = com.softwaremill.tagging.Tag[U]

  @deprecated("Use com.softwaremill.tagging instead")
  type @@[T, +U] = com.softwaremill.tagging.@@[T, U]

  @deprecated("Use com.softwaremill.tagging instead")
  type Tagged[T, +U] = com.softwaremill.tagging.Tagged[T, U]

  implicit class Tagger[T](t: T) {
    @deprecated("Use com.softwaremill.tagging instead")
    def taggedWith[U]: T @@ U = new com.softwaremill.tagging.Tagger(t).taggedWith[U]
  }

  implicit class AndTagger[T, U](t: T @@ U) {
    @deprecated("Use com.softwaremill.tagging instead")
    def andTaggedWith[V]: T @@ (U with V) = new com.softwaremill.tagging.AndTagger[T, U](t).andTaggedWith[V]
  }
}
