package com.softwaremill.play24

import com.softwaremill.play24.dao.{SupplierDao, CoffeeDao}
import com.softwaremill.play24.models.{Supplier, Coffee}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class Seed(
  val dbConfig: DatabaseConfig[JdbcProfile],
  val coffeeDao: CoffeeDao,
  val supplierDao: SupplierDao
) {

  import dbConfig.driver.api._
  val db = dbConfig.db

  def run(): Unit = {
    val setup = DBIO.seq(
      // Create the tables, including primary and foreign keys
      sqlu"DROP ALL OBJECTS",
      (supplierDao.query.schema ++ coffeeDao.query.schema).create,

      // Insert some suppliers
      supplierDao.query += Supplier(101, "Acme, Inc.", "99 Market Street", "Groundsville", "CA", "95199"),
      supplierDao.query += Supplier( 49, "Superior Coffee", "1 Party Place",    "Mendocino",    "CA", "95460"),
      supplierDao.query += Supplier(150, "The High Ground", "100 Coffee Lane",  "Meadows",      "CA", "93966"),
      // Equivalent SQL code:
      // insert into SUPPLIERS(SUP_ID, SUP_NAME, STREET, CITY, STATE, ZIP) values (?,?,?,?,?,?)

      // Insert some coffees (using JDBC's batch insert feature, if supported by the DB)
      coffeeDao.query ++= Seq(
        Coffee("Colombian",         101, 7.99, 0, 0),
        Coffee("French_Roast",       49, 8.99, 0, 0),
        Coffee("Espresso",          150, 9.99, 0, 0),
        Coffee("Colombian_Decaf",   101, 8.99, 0, 0),
        Coffee("French_Roast_Decaf", 49, 9.99, 0, 0)
      )
      // Equivalent SQL code:
      // insert into COFFEES(COF_NAME, SUP_ID, PRICE, SALES, TOTAL) values (?,?,?,?,?)
    )

    Await.result(db.run(setup), 30 seconds)
  }
}
