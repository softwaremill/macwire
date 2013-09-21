package com.softwaremill.macwire

private[macwire] object Util {
  def firstNotEmpty[T](fs: (() => List[T])*): Option[List[T]] = {
    for (f <- fs) {
      val r = f()
      if (!r.isEmpty) return Some(r)
    }

    None
  }
}
