package com.softwaremill.macwire

object WorkpadApp extends App {

  class Berry(var name: String)

  case class Basket(berry: Berry)

  lazy val blackberry: Berry = new Berry("blackberry")

  val basket: Basket = {
    lazy val raspberry: Berry = new Berry("raspberry")
    wire[Basket]
  }

  println(basket.berry.name) // blackberry
}

