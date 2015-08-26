package com.softwaremill.play24.dao

import com.softwaremill.play24.models.{Supplier, SupplierTable}
import slick.driver.H2Driver.api._

import scala.concurrent.Future

class SupplierDao(db: Database) {
  val query = TableQuery[SupplierTable]

  def all: Future[Seq[Supplier]] = db.run(query.result)

}
