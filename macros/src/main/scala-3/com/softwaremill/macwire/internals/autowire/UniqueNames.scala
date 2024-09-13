package com.softwaremill.macwire.internals.autowire

class UniqueNames:
  private var usedNames = Set.empty[String]
  def next(base: String): String =
    var i = 0
    var name = base
    while usedNames.contains(name) do
      i += 1
      name = s"$base$i"
    usedNames += name
    name
