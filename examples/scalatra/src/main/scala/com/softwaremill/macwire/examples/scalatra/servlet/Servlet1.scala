package com.softwaremill.macwire.examples.scalatra.servlet

import org.scalatra.ScalatraServlet
import org.scalatra.scalate.ScalateSupport
import com.softwaremill.macwire.examples.scalatra.logic.{SubmittedData, Service2, Service1}

class Servlet1(service1: Service1, service2: Service2, submittedData: SubmittedData)
    extends ScalatraServlet
    with ScalateSupport {
  get("/") {
    submittedData.data = params.getOrElse("data", null)
    contentType = "text/html"
    ssp("index", "service1status" -> service1.fetchStatus, "service2status" -> service2.fetchStatus)
  }
}
