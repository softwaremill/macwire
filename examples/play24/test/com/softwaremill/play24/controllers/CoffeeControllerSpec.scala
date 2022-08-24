package com.softwaremill.play24.controllers

import com.softwaremill.play24.models.Coffee
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

/**
 * Spec for testing controller logic, independent of external dependencies. Since our controllers don't really have any
 * logic, just testing the output
 */
class CoffeeControllerSpec extends Specification {
  "Coffee Controller" should {
    "return all" in new ControllerContext {
      val coffees = Seq(
        Coffee("coffee1", 1, 20, 5, 10),
        Coffee("coffee2", 2, 80, 10, 40),
        Coffee("coffee3", 2, 60, 50, 60),
        Coffee("coffee4", 3, 40, 40, 70),
        Coffee("coffee5", 4, 20, 30, 80)
      )

      coffeeDao.all returns Future.successful(coffees)
      val response = coffeeController.fetchAll()(FakeRequest())

      status(response) must be equalTo OK
      val json = contentAsJson(response)

      json mustEqual Json.toJson(coffees)
    }

    "return priced by coffee, supplier" in new ControllerContext {
      val coffees = Seq(
        ("coffee1", "supplier1"),
        ("coffee2", "supplier2"),
        ("coffee3", "supplier3")
      )

      coffeeDao.byPriceWithSuppliers(anyDouble) returns Future.successful(coffees)
      val response = coffeeController.priced(10)(FakeRequest())

      status(response) must be equalTo OK
      val json = contentAsJson(response)

      json mustEqual Json.toJson(coffees.toMap)
    }
  }
}
