package com.softwaremill.macwire

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class InstanceLookupTest extends FlatSpec with ShouldMatchers {
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

  def createInstanceMap(instances: AnyRef*): Map[Class[_], AnyRef] = Map(instances.map(i => i.getClass -> i): _*)

  val instanceMap1 = createInstanceMap(new X, new Y, new Z)
  val instanceMap2 = createInstanceMap(new Z, new M)
  val instanceMap3 = createInstanceMap(new N, new NN)
  val instanceMap4 = createInstanceMap(new A {}, new B {}, new C {}, new D {})

  it should "lookup correctly" in {
    testLookup(instanceMap1, classOf[X], 1)
    testLookup(instanceMap1, classOf[Y], 1)
    testLookup(instanceMap1, classOf[Z], 1)

    testLookup(instanceMap1, classOf[M], 0)

    testLookup(instanceMap1, classOf[A], 2)
    testLookup(instanceMap1, classOf[B], 1)
    testLookup(instanceMap1, classOf[C], 1)

    testLookup(instanceMap1, classOf[AnyRef], 3)

    testLookup(instanceMap2, classOf[A], 1)
    testLookup(instanceMap2, classOf[B], 2)
    testLookup(instanceMap2, classOf[C], 1)
    testLookup(instanceMap2, classOf[D], 1)

    testLookup(instanceMap3, classOf[N], 2)
    testLookup(instanceMap3, classOf[NN], 1)
    testLookup(instanceMap3, classOf[X], 0)

    testLookup(instanceMap4, classOf[A], 2)
    testLookup(instanceMap4, classOf[B], 2)
    testLookup(instanceMap4, classOf[C], 1)
    testLookup(instanceMap4, classOf[D], 1)

    testLookup(instanceMap4, classOf[X], 0)
    testLookup(instanceMap4, classOf[Y], 0)
  }

  def testLookup(map: Map[Class[_], AnyRef], cls: Class[_], expectedCount: Int) {
    val result = InstanceLookup(map).lookup(cls)
    result should have size (expectedCount)
    result.foreach(r => cls.isAssignableFrom(r.getClass) should be (true))
  }
}
