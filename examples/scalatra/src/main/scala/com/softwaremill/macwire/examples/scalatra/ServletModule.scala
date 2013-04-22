package com.softwaremill.macwire.examples.scalatra

import com.softwaremill.macwire.examples.scalatra.logic.LogicModule

trait ServletModule extends LogicModule {
  lazy val servlet1 = new Servlet1(service1, service2, submittedData)
  lazy val authServlet = new AuthServlet(loggedInUser)
}
