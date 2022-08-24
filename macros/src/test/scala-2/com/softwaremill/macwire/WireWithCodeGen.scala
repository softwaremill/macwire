package com.softwaremill.macwire

object WireWithCodeGen {

  def main(args: Array[String]) = {
    for(idx <- 0 until 22) {
      val tpes = (0 to idx).map(i => ('A' + i).toChar).mkString(",")
      println(s"def wireWith[$tpes,RES](factory: ($tpes) => RES): RES = macro MacwireMacros.wireWith_impl[RES]")
    }
  }
}
