package com.softwaremill.macwire.examples.scalatra.servlet

import com.softwaremill.macwire.examples.scalatra.logic.LogicModule
import com.softwaremill.macwire.scopes.ThreadLocalScope

trait ServletModule extends LogicModule {
  lazy val servlet1: Servlet1 = wire[Servlet1]
  lazy val authServlet: AuthServlet = wire[AuthServlet]

  lazy val scopeFilter = new ScopeFilter(request, session)

  lazy val request = new ThreadLocalScope
  lazy val session = new ThreadLocalScope
}
