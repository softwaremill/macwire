package com.softwaremill.macwire;

import java.lang.annotation.*;

/**
 * A Module groups classes of similar concern wired by macwire.
 * Modules can then be assembled to form larger modules.
 *
 * A dependency on module M1 by module M2 is simply defined by a constructor parameter:
 * <pre class="code">
 *     class M1
 *     class M2(m1: M1) // M2 depends on M1
 * </pre>
 *
 * Module dependencies are interesting when accessing the members of the dependency:
 * <pre class="code">
 *     class A
 *     class B(a : A)
 *
 *     class M1 {
 *         lazy val a = wire[A]
 *     }
 *
 *     class M2(m1: M1) {
 *         import m1.a
 *
 *         lazy val b = wire[B]
 *     }
 * </pre>
 *
 * But having to always import the module members can become cumbersome:
 * <pre class="code">
 *     class M9(m1: M1, m2: M2, m3) {
 *         import m1._
 *         import m2._
 *         import m3._
 *
 *         lazy val x = wire[X]
 *     }
 *
 *     class M10(m1: M1, m2: M2, m3: M3, m4: M4) {
 *         import m1._
 *         import m2._
 *         import m3._
 *         import m4._
 *
 *         lazy val y = wire[Y]
 *     }
 * </pre>
 *
 * That's when the {@code @Module} annotation comes into play. It will indicate that the members
 * of the class should be searched by macwire when it is depended upon:
 * <pre class="code">
 *     &#064;Module class M1
 *     &#064;Module class M2
 *     &#064;Module class M3
 *     &#064;Module class M4
 *
 *     class M9(m1: M1, m2: M2, m3) {
 *         // no import needed as M1, M2 and M3 are marked as &#064;Module
 *         lazy val x = wire[X]
 *     }
 *
 *     class M10(m1: M1, m2: M2, m3: M3, m4: M4) {
 *         lazy val y = wire[Y]
 *     }
 * </pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Inherited
public @interface Module {
}
