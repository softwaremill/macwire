package com.softwaremill.play24.modules

import com.softwaremill.macwire._
import com.softwaremill.play24.dao.{SupplierDao, CoffeeDao}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

trait DaoModule {
  def dbConfig: DatabaseConfig[JdbcProfile]

  lazy val coffeeDao = wire[CoffeeDao]
  lazy val supplierDao = wire[SupplierDao]
}
