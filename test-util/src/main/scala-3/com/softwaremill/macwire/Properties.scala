package com.softwaremill.macwire

import java.io.File

object Properties {
  def currentClasspath = sys.props("java.class.path")
}
