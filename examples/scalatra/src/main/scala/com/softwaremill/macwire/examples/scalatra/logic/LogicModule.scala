package com.softwaremill.macwire.examples.scalatra.logic

import com.softwaremill.macwire.Macwire
import com.softwaremill.macwire.scopes.Scope

trait LogicModule extends Macwire {
  lazy val loggedInUser: LoggedInUser = session(wire[LoggedInUser])
  lazy val submittedData: SubmittedData = request(wire[SubmittedData])
  lazy val service1: Service1 = wire[Service1]
  lazy val service2: Service2 = wire[Service2]
  lazy val service3: Service3 = wire[Service3]

  def session: Scope
  def request: Scope
}
