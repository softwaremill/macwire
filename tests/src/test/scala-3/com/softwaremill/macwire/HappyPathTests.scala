package com.softwaremill.macwire

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HappyPathTests extends AnyFreeSpec with Matchers {
    // "Happy path"  - {
    //     "anonFuncAndMethodsArgsWiredOk.success" in {
    //         object Module {
    //             val a = (a: A) => {
    //                 def b(b: B) = {
    //                     wire[C]
    //                 }
    //                 b _
    //             }
    //         }

    //         val aDep = new A()
    //         val bDep = new B()
    //         val c = Module.a(aDep)(bDep)


    //         c.a shouldBe aDep
    //         c.b shouldBe bDep
    //     }
    // }
  
    // "anonFuncArgsWiredOk.success" in {
    //     object Module {
    //         val a = new A()
    //         val c1 = (b: B) => wire[C]
    //         val c2 = (b: B) => {
    //             val c: C = {
    //                 System.getProperty("mary")
    //                 wire[C]
    //             }
    //             // do sthg
    //             System.getProperty("john")
    //             c
    //         }
    //     }

    //     val b = new B()
    //     val c1 = Module.c1(b)
    //     val c2 = Module.c2(b)

    //     require(c1.a eq Module.a)
    //     require(c1.b eq b)

    //     require(c2.a eq Module.a)
    //     require(c2.b eq b)

    // }

    "basic use case" in {
        case class A()
        case class B()
        case class C(a: A, b: B)

        object Test {
        lazy val a = A()
        lazy val b = B()
        lazy val c = wire[C]

        }

        Test.c shouldBe C(Test.a, Test.b)
    }
}

case class A()
case class B()
case class C(a: A, b: B)

// not always used
case class D(c: C)
case class E(a: A, c: C)

