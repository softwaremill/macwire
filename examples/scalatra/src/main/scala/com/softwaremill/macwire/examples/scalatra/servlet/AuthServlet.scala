package com.softwaremill.macwire.examples.scalatra.servlet

import org.scalatra.ScalatraServlet
import org.scalatra.scalate.ScalateSupport
import com.softwaremill.macwire.examples.scalatra.logic.LoggedInUser

class AuthServlet(loggedInUser: LoggedInUser) extends ScalatraServlet with ScalateSupport {
  get("/loginForm") {
    contentType = "text/html"
    ssp("loginForm")
  }

  post("/login") {
    loggedInUser.login(params("username"))
    redirect(url(""))
  }

  get("/logout") {
    session.invalidate()
    redirect(url(""))
  }
}
