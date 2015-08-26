package com.softwaremill.play24.modules

import play.api.db._
import slick.driver.H2Driver.api._

trait DatabaseModule extends DBComponents with HikariCPComponents {
  def dbApi: DBApi
  lazy val db = Database.forDataSource(dbApi.database("default").dataSource)
}
