package com.softwaremill.play24

import _root_.controllers.Assets
import com.softwaremill.play24.dao.{SupplierDao, CoffeeDao}
import com.softwaremill.play24.modules.{DatabaseModule, ControllerModule, DaoModule}
import play.api.ApplicationLoader.Context
import play.api._
import play.api.libs.ws.ning.NingWSComponents
import play.api.routing.Router
import router.Routes
import com.softwaremill.macwire._

import scala.concurrent.ExecutionContext

class AppApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents
extends BuiltInComponents
with NingWSComponents // for wsClient
with DatabaseModule // Database injection
with DaoModule
with ControllerModule // Application controllers
{
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  lazy val assets: Assets = wire[Assets]
  lazy val router: Router = wire[Routes] withPrefix "/"

  // The seed method is here just for demonstration purposes. Ideally this will be run in a task.
  def coffeeDoa: CoffeeDao
  def supplierDoa: SupplierDao
  val seed = wire[Seed]
  seed.run()
}


