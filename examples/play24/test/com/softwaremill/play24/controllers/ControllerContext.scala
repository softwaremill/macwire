package com.softwaremill.play24.controllers

import com.softwaremill.play24.modules.{MockWsClient, MockDaoModule, ControllerModule}
import org.specs2.matcher.MustThrownExpectations
import org.specs2.specification.Scope

import scala.concurrent.ExecutionContext

trait ControllerContext
  extends ControllerModule
  with MockDaoModule
  with MockWsClient
  with Scope
  with MustThrownExpectations
{
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
