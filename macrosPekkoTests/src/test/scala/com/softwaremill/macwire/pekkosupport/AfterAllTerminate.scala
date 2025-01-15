package com.softwaremill.macwire.pekkosupport

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.dispatch.MonitorableThreadFactory
import scala.concurrent.Await
import scala.concurrent.duration._

object AfterAllTerminate {

  /** We need to terminate ActorSystem. It can be done in test script in last line by simply calling
    * `system.terminate()`. However above solution can terminate the `actorSystem` sooner than all messages are being
    * delivered. That will result in ugly messages in test logs which are not related to bugs:
    *
    * {{{[INFO] [01/31/2017 19:33:21.407] [wireProps-9-subtypeDependencyInScope-org.apache.pekko.actor.default-dispatcher-6] [org.apache.pekko://wireProps-9-subtypeDependencyInScope/user/someActor] Message [java.lang.String] from Actor[org.apache.pekko://wireProps-9-subtypeDependencyInScope/deadLetters] to Actor[org.apache.pekko://wireProps-9-subtypeDependencyInScope/user/someActor#-2046131540] was not delivered. [1] dead letters encountered. This logging can be turned off or adjusted with configuration settings 'org.apache.pekko.log-dead-letters' and 'org.apache.pekko.log-dead-letters-during-shutdown'.[info] wireActor-11-toManyInjectAnnotations.failure}}}
    *
    * If we terminate `ActorSystem` using this hook the system will be terminated after all messages are being
    * delivered. Logs become cleaner.
    */
  def apply(system: ActorSystem): Unit = Runtime.getRuntime.addShutdownHook(
    MonitorableThreadFactory(
      name = "monitoring-thread-factory",
      daemonic = false,
      contextClassLoader = Some(Thread.currentThread().getContextClassLoader)
    ).newThread(new Runnable {
      override def run(): Unit = {
        val terminate = system.terminate()
        Await.result(terminate, 10.seconds)
      }
    })
  )

}
