package com.softwaremill.macwire.examples.scalatra.logic

import com.softwaremill.macwire.Macwire

trait LogicModule extends Macwire {
  lazy val loggedInUser = wire[LoggedInUser]
  lazy val submittedData = wire[SubmittedData]
  lazy val service1 = wire[Service1]
  lazy val service2 = wire[Service2]
  lazy val service3 = wire[Service3]
}
