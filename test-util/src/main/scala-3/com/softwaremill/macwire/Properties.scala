package com.softwaremill.macwire

import java.io.File

object Properties {
  def currentClasspath = sys.props.getOrElse("macwire.test.classpath", sys.props("java.class.path"))
}
