package com.softwaremill.play24.dao

import com.softwaremill.play24.models.{CoffeeTable, Coffee}
import slick.driver.H2Driver.api._

import scala.concurrent.Future

class CoffeeDao(db: Database, supplierDao: SupplierDao) {
  val query = TableQuery[CoffeeTable]

  def all: Future[Seq[Coffee]] = db.run(query.result)

  // Table join example
  def byPriceWithSuppliers(price: Double): Future[Seq[(String, String)]] = {
    db.run {
      val q2 = for {
        c <- query if c.price < price
        s <- supplierDao.query if s.id === c.supID
      } yield (c.name, s.name)

      q2.result
    }
  }
}
