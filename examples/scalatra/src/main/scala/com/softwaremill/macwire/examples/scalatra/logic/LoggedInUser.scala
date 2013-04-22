package com.softwaremill.macwire.examples.scalatra.logic

class LoggedInUser {
  private var loggedInUsername: Option[String] = None

  def login(username: String) {
    loggedInUsername = Some(username)
  }

  def username: Option[String] = loggedInUsername
}
