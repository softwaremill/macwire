package com.softwaremill.macwire.autocats

package object internals {
  def duplicates[T](s: Seq[T]): Seq[T] =
    s.groupBy(identity).collect { case (x, ys) if ys.lengthCompare(1) > 0 => x }.toSeq
}
