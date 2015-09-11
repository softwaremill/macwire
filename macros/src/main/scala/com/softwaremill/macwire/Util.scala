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
      else {
        // some arbitrary comparison that should retain the compare semantic
        val hashCodeCompare = x.hashCode().compareTo(y.hashCode())
        // it might happen that 2 trees have the same hashCode but are structurally different:
        // Tree hashCode is `System.identityHashCode` which does not guarantee that 2 different
        // object have different hashCodes.
        if( hashCodeCompare == 0 ) -1 else hashCodeCompare
      }
    }
  }
}
