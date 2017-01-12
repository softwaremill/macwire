package com.softwaremill.macwire.internals

private[macwire] final class Logger {
  var ident = 0

  /** Log at `DEBUG` level */
  def apply(msg: => String): Unit = apply(msg, DEBUG_LEVEL)

  def trace(msg: => String) = apply(msg, TRACE_LEVEL)

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

  private def apply(msg: => String, desiredLevel: Int): Unit = {
    if (desiredLevel >= level) {
      val prefix = "   " * ident
      val logLevelName = if(desiredLevel == DEBUG_LEVEL) "debug" else "trace"
      println(s"$prefix[$logLevelName] $msg")
    }
  }

  private val TRACE_LEVEL = 1
  private val DEBUG_LEVEL = 2
  private val NO_LOG_LEVEL = 3

  private val level = if (System.getProperty("macwire.trace") != null) {
    TRACE_LEVEL
  } else if (System.getProperty("macwire.debug") != null) {
    DEBUG_LEVEL
  } else {
    NO_LOG_LEVEL
  }
}
