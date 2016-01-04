package com.softwaremill.play24.controllers

import com.softwaremill.play24.dao.SupplierDao
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext

class SupplierController(
  supplierDao: SupplierDao
)(implicit ec: ExecutionContext) extends Controller {

  def fetchAll() = Action.async { request =>
    supplierDao.all.map { suppliers =>
      Ok(Json.toJson(suppliers))
    }
  }

}
