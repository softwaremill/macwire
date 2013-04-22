package com.softwaremill.macwire.examples.scalatra.logic

class Service1(loggedInUser: LoggedInUser, submittedData: SubmittedData) {
  def fetchStatus = {
    s"This is Service1. " +
      s"Logged in user: ${loggedInUser.username}, " +
      s"submitted data: ${submittedData.data}, " +
      s"hash code: ${System.identityHashCode(this)}"
  }
}
