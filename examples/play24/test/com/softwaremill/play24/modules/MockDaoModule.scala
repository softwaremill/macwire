package com.softwaremill.play24.modules

import com.softwaremill.play24.dao.{CoffeeDao, SupplierDao}
import org.specs2.mock.Mockito

trait MockDaoModule extends Mockito {
  lazy val coffeeDoa = mock[CoffeeDao]
  lazy val supplierDoa = mock[SupplierDao]
}
