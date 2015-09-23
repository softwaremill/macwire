package com.softwaremill.macwire

import scala.reflect.macros.blackbox

private[macwire] object Util {

  /**
   * An ordering that uses structural equality on Tree;
   * `equals` on trees is reference equality.
   */
  def structuralTreeOrdering[C <: blackbox.Context](c : C): Ordering[c.Tree] = new Ordering[c.Tree] {
    override def compare(x: c.Tree, y: c.Tree): Int = {
      if (x.equalsStructure(y)) 0
      else x.toString().compareTo(y.toString())
    }
  }
}
