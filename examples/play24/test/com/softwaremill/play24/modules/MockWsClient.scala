package com.softwaremill.play24.modules

import org.specs2.mock.Mockito
import play.api.libs.ws.WSClient

trait MockWsClient extends Mockito {
  lazy val wsClient = mock[WSClient]
}
