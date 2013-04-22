package com.softwaremill.macwire.examples.scalatra.logic

trait LogicModule {
  lazy val loggedInUser = new LoggedInUser
  lazy val submittedData = new SubmittedData
  lazy val service1 = new Service1(loggedInUser, submittedData)
  lazy val service2 = new Service2(submittedData, service3, loggedInUser)
  lazy val service3 = new Service3
}
