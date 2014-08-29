package com.softwaremill

case class MoneyEarningBusinessLogic() {
  def earnMoney(howMuch: Int): String = {
    s"Just earned another $$$howMuch!"
  }
}
