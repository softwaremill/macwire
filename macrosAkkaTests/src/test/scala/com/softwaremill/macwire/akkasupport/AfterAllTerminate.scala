package com.softwaremill.macwire.akkasupport

import akka.actor.ActorSystem
import akka.dispatch.MonitorableThreadFactory
import scala.concurrent.Await
import scala.concurrent.duration._

object AfterAllTerminate {

  def apply(system: ActorSystem) = Runtime.getRuntime.addShutdownHook(
    MonitorableThreadFactory(
      name = "monitoring-thread-factory",
      daemonic = false,
      contextClassLoader = Some(Thread.currentThread().getContextClassLoader)).newThread(new Runnable {
      override def run(): Unit = {
        val terminate = system.terminate()
        Await.result(terminate, 10 seconds)
      }
    })
  )

}
