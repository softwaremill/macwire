package com.softwaremill.play24.modules

import com.softwaremill.macwire._
import com.softwaremill.play24.dao.{SupplierDao, CoffeeDao}
import slick.driver.H2Driver.api._

trait DaoModule {
  def db: Database

  lazy val coffeeDoa = wire[CoffeeDao]
  lazy val supplierDoa = wire[SupplierDao]
}
