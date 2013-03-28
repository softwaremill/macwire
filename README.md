MacWire
=======

MacWire generates wiring code for values found within a trait using [Scala Macros](http://scalamacros.org/).

MacWire can be in many cases a replacement for DI containers, offering greater control on how classes are instantiated,
and using only language (Scala) mechanisms.

A similar project for Java is [Dagger](https://github.com/square/dagger).

For more motivation behind the project see also these blogs:

* [Dependency injection with Scala macros: auto-wiring](http://www.warski.org/blog/2013/03/dependency-injection-with-scala-macros-auto-wiring/)

The library has no dependencies, and itself is not a runtime dependency. It only needs to be available on the classpath
during compilation.

Example usage:

    class A()
    class B()
    class C(a: A, b: B)
    class D(c: C)

    trait Test {
        import com.softwaremill.macwire.MacwireMacros._

        lazy val theA = wire[A]
        lazy val theB = wire[B]
        lazy val theC = wire[C]
        lazy val theD = wire[D]
    }

will generate:

    trait Test {
        lazy val theA = new A()
        lazy val theB = new B()
        lazy val theC = new C(theA, theB)
        lazy val theD = new D(theC)
    }

`lazy val` vs. `val`
--------------------

It is safer to use a `lazy val`, as when using `val` if a value is forward-referenced, it's value during initialization
will be `null`.

Scopes
------

* singleton: `lazy val` / `val`
* dependent - separate instance for each dependency usage: `def`

More scopes to come!

Installation, using with SBT
----------------------------

The jars are deployed to [Sonatype's OSS repository](https://oss.sonatype.org/content/repositories/snapshots/com/softwaremill/macwire/).
To use MacWire in your project, add a dependency:

    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    libraryDependencies += "com.softwaremill.macwire" %% "core" % "0.1-SNAPSHOT"

Limitations
-----------

When referencing wired values within the trait, e.g.:

    class A()
    class B(a: A)

    lazy val theA = wire[A]
     // reference to theA; if for some reason we need explicitly write the constructor call
    lazy val theB = new B(thaA)

to avoid recursive type compiler errors, the referenced wired value needs a type ascription, e.g.:

    lazy val theA: A = wire[A]

Also, wiring will probably not work properly for traits and classes defined inside the containing trait/class, or in
super traits/classes.

Debugging
---------

The print debugging information on what MacWire does when looking for values, and what code is generated, set the
`macwire.debug` system property. E.g. with SBT, just add a `System.setProperty("macwire.debug", "")` line to your
build file.

TODO
----

* subtyping support
* parent support
* testing docs
* factories
* inject a list of dependencies - of a given type