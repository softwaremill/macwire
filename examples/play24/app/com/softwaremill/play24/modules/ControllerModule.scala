package com.softwaremill.play24.modules

import com.softwaremill.macwire._
import com.softwaremill.play24.controllers.{SupplierController, CoffeeController}
import com.softwaremill.play24.dao.{CoffeeDao, SupplierDao}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

trait ControllerModule {

  // Dependencies
  implicit def ec: ExecutionContext
  def wsClient: WSClient
  def supplierDoa: SupplierDao
  def coffeeDoa: CoffeeDao

  // Controllers
  lazy val supplierController = wire[SupplierController]
  lazy val coffeeController = wire[CoffeeController]
}
