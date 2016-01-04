package com.softwaremill.play24.dao

import com.softwaremill.play24.models.{Coffee, CoffeeTable}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.Future

class CoffeeDao(dbConfig: DatabaseConfig[JdbcProfile], supplierDao: SupplierDao) {
  import dbConfig.driver.api._

  val db = dbConfig.db
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
