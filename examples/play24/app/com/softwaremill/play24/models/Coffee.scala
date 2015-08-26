package com.softwaremill.play24.models

import play.api.libs.json.Json
import slick.driver.H2Driver.api._

case class Coffee (
  name: String,
  supId: Int,
  price: Double,
  sales: Int,
  total: Int
)

object Coffee {
  implicit val format = Json.format[Coffee]
}


class CoffeeTable(tag: Tag) extends Table[Coffee](tag, "COFFEES") {
  def name = column[String]("COF_NAME", O.PrimaryKey)
  def supID = column[Int]("SUP_ID")
  def price = column[Double]("PRICE")
  def sales = column[Int]("SALES")
  def total = column[Int]("TOTAL")
  def * = (name, supID, price, sales, total) <> ((Coffee.apply _).tupled, Coffee.unapply)
  // A reified foreign key relation that can be navigated to create a join
  def supplier = foreignKey("SUP_FK", supID, TableQuery[SupplierTable])(_.id)
}