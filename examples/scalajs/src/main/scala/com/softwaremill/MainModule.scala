package com.softwaremill

import com.softwaremill.macwire.MacwireMacros._

trait MainModule {
  lazy val moneyEarningBusinessLogic = wire[MoneyEarningBusinessLogic]
  lazy val earnLotsOfMoney = wire[EarnLotsOfMoney]
}
