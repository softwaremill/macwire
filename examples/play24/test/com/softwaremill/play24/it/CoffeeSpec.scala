package com.softwaremill.play24.it

import play.api.test.{FakeRequest, PlaySpecification}

class CoffeeSpec extends PlaySpecification {
  "/coffee/all" should {
    "return all coffees" in new IntegrationContext {
      val response = route(FakeRequest(GET, "/coffee/all"))

      println(status(response.get))

      response must beSome.which (status(_) == OK)
    }
  }

  "/coffee/priced/" should {
    "return priced coffees" in new IntegrationContext {
      val response = route(FakeRequest(GET, "/coffee/priced/9"))

      response must beSome.which (status(_) == OK)
    }
  }
}
