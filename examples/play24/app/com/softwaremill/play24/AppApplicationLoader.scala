package com.softwaremill.play24

import _root_.controllers.Assets
import akka.actor.ActorSystem
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

    // make sure logging is configured
    Logger.configure(context.environment)

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
  lazy val router: Router = {
    lazy val prefix = "/"
    wire[Routes]
  }

  wire[Z]

  // The seed method is here just for demonstration purposes. Ideally this will be run in a task.
  def coffeeDao: CoffeeDao
  def supplierDao: SupplierDao
  val seed = wire[Seed]
  seed.run()
}

class Z(as: ActorSystem)
