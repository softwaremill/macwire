package com.softwaremill.macwire.examples.scalatra

import com.softwaremill.macwire.examples.scalatra.logic.LogicModule
import com.softwaremill.macwire.scopes.ThreadLocalScope

trait ServletModule extends LogicModule {
  lazy val servlet1 = wire[Servlet1]
  lazy val authServlet = wire[AuthServlet]

  def session = new ThreadLocalScope
  def request = new ThreadLocalScope
}
