package com.softwaremill.macwire.scopes

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import com.softwaremill.macwire.Macwire

/*
Alternative syntax:

wireScoped[Cls](scope)

translated to:

new Cls(a, b, c) {
   key = "123-123-123"
   override def x(y, z) = scope.getOrElse(key, new Cls(a, b, c)).x(y, z)
}

but:
- only works for wired values - still need something for values created in another way
- special wire version

===

scoped(scope, wire[Cls])

but
- special handling for wire, new ... needed; still wouldn't work in the general case
- can you do macro inside a macro?

*/
class ThreadLocalScopeTest extends FlatSpec with ShouldMatchers {
  it should "store object values per-storage" in {
    // Given
    val storage1 = new collection.mutable.HashMap[String, Any]()
    val storage2 = new collection.mutable.HashMap[String, Any]()
    val storage3 = new collection.mutable.HashMap[String, Any]()

    // When
    val scope = new ThreadLocalScope()
    val bean = scope(new Stateful1)

    scope.withStorage(storage1) {
      bean.data = "2"
    }

    scope.withStorage(storage2) {
      bean.data = "3"
    }

    // Then
    scope.associate(storage1)
    bean.data should be ("2")
    scope.disassociate()

    scope.associate(storage2)
    bean.data should be ("3")
    scope.disassociate()

    scope.associate(storage3)
    bean.data should be ("1")
    scope.disassociate()
  }

  it should "proxy classes without a default constructor" in {
    // Given
    val storage = new collection.mutable.HashMap[String, Any]()

    // When
    val scope = new ThreadLocalScope()
    val bean = scope(new Stateful2("10", 11))

    scope.withStorage(storage) {
      bean.data = "2"
    }

    // Then
    scope.associate(storage)
    bean.data should be ("2")
    scope.disassociate()
  }

  it should "work with wire" in {
    // Given
    val storage = new collection.mutable.HashMap[String, Any]()

    // When
    val scope = new ThreadLocalScope()

    object Beans extends Macwire {
      val stateful1 = scope(wire[Stateful1])
      val service1 = wire[Service1]
    }

    scope.withStorage(storage) {
      Beans.stateful1.data = "2"
    }

    // Then
    scope.associate(storage)
    Beans.service1.fetchData should be ("2")
    scope.disassociate()
  }
}

class Stateful1 {
  var data = "1"
}

class Stateful2(x: String, y: Int) {
  var data = "1"
}

class Service1(stateful1: Stateful1) {
  def fetchData = stateful1.data
}