package com.softwaremill.macwire.examples.scalatra.logic

import com.softwaremill.macwire.Macwire
import com.softwaremill.macwire.scopes.Scope

trait LogicModule extends Macwire {
  lazy val loggedInUser = session(wire[LoggedInUser])
  lazy val submittedData = request(wire[SubmittedData])
  lazy val service1 = wire[Service1]
  lazy val service2 = wire[Service2]
  lazy val service3 = wire[Service3]

  def session: Scope
  def request: Scope
}
