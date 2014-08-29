package com.softwaremill

case class EarnLotsOfMoney(moneyEarningBusinessLogic: MoneyEarningBusinessLogic) {
  def doIt(): String = {
    moneyEarningBusinessLogic.earnMoney(1000000)
  }
}
