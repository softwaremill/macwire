package com.softwaremill.play24.controllers

import com.softwaremill.play24.dao.CoffeeDao
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext

class CoffeeController(
  coffeeDao: CoffeeDao
)(implicit ec: ExecutionContext) extends Controller {

  def fetchAll() = Action.async { request =>
    coffeeDao.all.map { coffees =>
      Ok(Json.toJson(coffees))
    }
  }

  def priced(price: Double) = Action.async { request =>
    coffeeDao.byPriceWithSuppliers(price).map { result =>
      Ok(Json.toJson(result.toMap))
    }
  }
}
