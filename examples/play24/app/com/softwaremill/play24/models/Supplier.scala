package com.softwaremill.play24.models

import play.api.libs.json.Json
import slick.driver.H2Driver.api._

case class Supplier(
  id: Int,
  name: String,
  street: String,
  city: String,
  state: String,
  zip: String
)

object Supplier {
  implicit val format = Json.format[Supplier]
}

class SupplierTable(tag: Tag) extends Table[Supplier](tag, "SUPPLIERS") {
  def id = column[Int]("SUP_ID", O.PrimaryKey) // This is the primary key column
  def name = column[String]("SUP_NAME")
  def street = column[String]("STREET")
  def city = column[String]("CITY")
  def state = column[String]("STATE")
  def zip = column[String]("ZIP")
  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id, name, street, city, state, zip)  <> ((Supplier.apply _).tupled, Supplier.unapply)
}
