package com.softwaremill.macwire

import scala.reflect.macros.blackbox

private[macwire] class PositionUtil[C <: blackbox.Context](val c: C) {
  import c.universe._

  def samePosition(pos1: Position, pos2: Position): Boolean = {
    // sometimes same positions are represented as different case classes (e.g. offset-position and range-position)
    // some positions don't implement .point
    pos1 == pos2 || (try {
      pos1.point == pos2.point && pos1.source == pos2.source
    } catch { case _: Exception => false })
  }
}
