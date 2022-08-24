package com.softwaremill.macwire

object WireWithCodeGen {

  def main(args: Array[String]) = {
    for(idx <- 0 until 22) {
      val tpes = (0 to idx).map(i => ('A' + i).toChar).mkString(",")
      println(s"inline def wireWith[$tpes,RES](inline factory: ($tpes) => RES): RES = $${ MacwireMacros.wireWith_impl[RES]('factory) }")
    }
  }
}
