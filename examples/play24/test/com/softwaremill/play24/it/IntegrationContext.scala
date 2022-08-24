package com.softwaremill.play24.it

import com.softwaremill.play24.AppApplicationLoader
import play.api.test.WithApplicationLoader

class IntegrationContext extends WithApplicationLoader(
  applicationLoader = new AppApplicationLoader
)
