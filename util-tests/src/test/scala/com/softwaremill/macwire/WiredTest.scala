package com.softwaremill.macwire

import com.softwaremill.macwire.Wired.InstanceFactoryMap
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class WiredTest extends AnyFlatSpec with Matchers {
  import WiredTest._

  def createInstanceFactoryMap(instances: AnyRef*): InstanceFactoryMap = Map(instances.map(i => i.getClass -> (() => i)): _*)

  val implsMap1 = createInstanceFactoryMap(new X, new Y, new Z)
  val implsMap2 = createInstanceFactoryMap(new Z, new M)
  val implsMap3 = createInstanceFactoryMap(new N, new NN)
  val implsMap4 = createInstanceFactoryMap(new A {}, new B {}, new C {}, new D {})

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

  def testLookup(map: InstanceFactoryMap, cls: Class[_], expectedCount: Int): Unit = {
    val result = new Wired(map).lookup(cls)
    result should have size (expectedCount)
    result.foreach(r => cls.isAssignableFrom(r.getClass) should be (true))
  }

  it should "expand the wired instance factories map with the given instances" in {
    // given
    val original = new Wired(createInstanceFactoryMap(new X))

    // when
    val wired = original.withInstances(new Y, new Z)

    // then
    original.lookup(classOf[A]).size should be (0)
    wired.lookup(classOf[A]).size should be (2)

    original.lookup(classOf[Z]).size should be (0)
    wired.lookup(classOf[Z]).size should be (1)
  }

  it should "expand the wired instance factories map with the given instance factory" in {
    // given
    val original = new Wired(createInstanceFactoryMap(new X))

    // when
    val wired = original.withInstanceFactory(() => new Y)

    // then
    original.lookup(classOf[Y]).size should be (0)
    wired.lookup(classOf[Y]).size should be (1)

    (wired.lookup(classOf[Y]).apply(0) eq wired.lookup(classOf[Y]).apply(0)) should be (false)
  }

  val x = new X
  val y = new Y
  val z = new Z
  val m = new M

  val wiredXYZM = new Wired(createInstanceFactoryMap(x, y, z, m))

  it should "create an instance of a class with no dependencies" in {
    val result = wiredXYZM.wireClassInstance(classOf[NoDeps])

    result should not be (null)
    result.getClass should be (classOf[NoDeps])
  }

  it should "create an instance of a class with one exact dependency" in {
    val result = wiredXYZM.wireClassInstance(classOf[OneExactDep])

    result.x should be (x)
  }

  it should "create an instance of a class with one subtype dependency" in {
    val result = wiredXYZM.wireClassInstance(classOf[OneSubtypeDep])

    result.c should be (z)
  }

  it should "create an instance of a class with multiple dependencies" in {
    val result = wiredXYZM.wireClassInstance(classOf[MultipleDeps])

    result.y should be (y)
    result.c should be (z)
  }

  it should "create an instance of a class given by string" in {
    val result = wiredXYZM.wireClassInstanceByName(classOf[OneExactDep].getName)

    result.isInstanceOf[OneExactDep] should be (true)
    result.asInstanceOf[OneExactDep].x should be (x)
  }

  it should "fail to create an instance of a class if dependencies are not found" in {
    intercept[InstantiationException] {
      wiredXYZM.wireClassInstance(classOf[InvalidDep])
    }
  }

  it should "fail to create an instance of a class if multiple dependencies are found" in {
    intercept[InstantiationException] {
      wiredXYZM.wireClassInstance(classOf[InvalidMultipleDep])
    }
  }
}

object WiredTest {
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

  class NoDeps
  class OneExactDep(val x: X)
  class OneSubtypeDep(val c: C)
  class MultipleDeps(val y: Y, val c: C)
  class InvalidDep(val n: N)
  class InvalidMultipleDep(val a: A)
}
