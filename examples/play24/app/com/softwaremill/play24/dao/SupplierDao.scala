package com.softwaremill.play24.dao

import com.softwaremill.play24.models.{Supplier, SupplierTable}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.Future

class SupplierDao(dbConfig: DatabaseConfig[JdbcProfile]) {
  import dbConfig.driver.api._

  val db = dbConfig.db
  val query = TableQuery[SupplierTable]

  def all: Future[Seq[Supplier]] = db.run(query.result)
}
