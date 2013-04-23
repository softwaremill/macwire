package com.softwaremill.macwire.examples.scalatra

import com.softwaremill.macwire.examples.scalatra.logic.LogicModule

trait ServletModule extends LogicModule {
  lazy val servlet1 = wire[Servlet1]
  lazy val authServlet = wire[AuthServlet]
}
