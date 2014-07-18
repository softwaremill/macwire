package com.softwaremill.macwire

import org.scalatest.FlatSpec
import org.scalatest.ShouldMatchers
import MacwireMacros.ImplsMap

class ImplLookupTest extends FlatSpec with ShouldMatchers {
  import ImplLookupTest._

  trait A
  trait B
  trait C extends A with B
  trait D

  class X
  class Y extends A
  class Z extends C
  class M extends B with D

  class N
  class NN extends N

  val implsMap1 = createImplsMap(new X, new Y, new Z)
  val implsMap2 = createImplsMap(new Z, new M)
  val implsMap3 = createImplsMap(new N, new NN)
  val implsMap4 = createImplsMap(new A {}, new B {}, new C {}, new D {})

  it should "lookup correctly" in {
    testLookup(implsMap1, classOf[X], 1)
    testLookup(implsMap1, classOf[Y], 1)
    testLookup(implsMap1, classOf[Z], 1)

    testLookup(implsMap1, classOf[M], 0)

    testLookup(implsMap1, classOf[A], 2)
    testLookup(implsMap1, classOf[B], 1)
    testLookup(implsMap1, classOf[C], 1)

    testLookup(implsMap1, classOf[AnyRef], 3)

    testLookup(implsMap2, classOf[A], 1)
    testLookup(implsMap2, classOf[B], 2)
    testLookup(implsMap2, classOf[C], 1)
    testLookup(implsMap2, classOf[D], 1)

    testLookup(implsMap3, classOf[N], 2)
    testLookup(implsMap3, classOf[NN], 1)
    testLookup(implsMap3, classOf[X], 0)

    testLookup(implsMap4, classOf[A], 2)
    testLookup(implsMap4, classOf[B], 2)
    testLookup(implsMap4, classOf[C], 1)
    testLookup(implsMap4, classOf[D], 1)

    testLookup(implsMap4, classOf[X], 0)
    testLookup(implsMap4, classOf[Y], 0)
  }

  def testLookup(map: ImplsMap, cls: Class[_], expectedCount: Int) {
    val result = ImplLookup(map).lookup(cls)
    result should have size (expectedCount)
    result.foreach(r => cls.isAssignableFrom(r.getClass) should be (true))
  }
}

object ImplLookupTest {
  def createImplsMap(instances: AnyRef*): ImplsMap = Map(instances.map(i => i.getClass -> (() => i)): _*)
}