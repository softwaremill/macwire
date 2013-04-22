package com.softwaremill.macwire

private[macwire] class Debug {
  var ident = 0

  def apply(msg: => String) {
    if (enabled) {
      val prefix = "   " * (ident - 1)
      println(s"$prefix[debug] $msg")
    }
  }

  def withBlock[T](msg: => String)(block: => T): T = {
    apply(msg)
    beginBlock()
    try {
      block
    } finally {
      endBlock()
    }
  }

  def beginBlock() {
    ident += 1
  }

  def endBlock() {
    ident -= 1
  }

  private val enabled = System.getProperty("macwire.debug") != null
}
