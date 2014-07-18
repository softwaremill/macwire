package com.softwaremill.macwire

import org.scalatest.{FlatSpec, ShouldMatchers}

class DynamicInstantiateTest extends FlatSpec with ShouldMatchers {
  import InstanceLookupTest._
  import DynamicInstantiateTest._

  val x = new X
  val y = new Y
  val z = new Z
  val m = new M

  val instanceLookup = new InstanceLookup(createImplsMap(x, y, z, m))

  val dynamicInstantiate = new DynamicInstantiate(instanceLookup)

  it should "create an instance of a class with no dependencies" in {
    val result = dynamicInstantiate.instantiate(classOf[NoDeps])

    result should not be (null)
    result.getClass should be (classOf[NoDeps])
  }

  it should "create an instance of a class with one exact dependency" in {
    val result = dynamicInstantiate.instantiate(classOf[OneExactDep])

    result.x should be (x)
  }

  it should "create an instance of a class with one subtype dependency" in {
    val result = dynamicInstantiate.instantiate(classOf[OneSubtypeDep])

    result.c should be (z)
  }

  it should "create an instance of a class with multiple dependencies" in {
    val result = dynamicInstantiate.instantiate(classOf[MultipleDeps])

    result.y should be (y)
    result.c should be (z)
  }

  it should "create an instance of a class given by string" in {
    val result = dynamicInstantiate.instantiate(classOf[OneExactDep].getName)

    result.isInstanceOf[OneExactDep] should be (true)
    result.asInstanceOf[OneExactDep].x should be (x)
  }

  it should "fail to create an instance of a class if dependencies are not found" in {
    intercept[InstantiationException] {
      dynamicInstantiate.instantiate(classOf[InvalidDep])
    }
  }

  it should "fail to create an instance of a class if multiple dependencies are found" in {
    intercept[InstantiationException] {
      dynamicInstantiate.instantiate(classOf[InvalidMultipleDep])
    }
  }
}

object DynamicInstantiateTest {
  trait A
  trait B
  trait C extends A with B
  trait D

  class X
  class Y extends A
  class Z extends C
  class M extends B with D
  class N

  class NoDeps
  class OneExactDep(val x: X)
  class OneSubtypeDep(val c: C)
  class MultipleDeps(val y: Y, val c: C)
  class InvalidDep(val n: N)
  class InvalidMultipleDep(val a: A)
}