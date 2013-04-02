MacWire
=======

MacWire generates `new` instance creation code of given classes, using values in scope for constructor parameters,
with the help of [Scala Macros](http://scalamacros.org/).

MacWire helps to implement the Dependency Injection (DI) pattern, by removing the need to write the
class-wiring code by hand. Instead, it is enough to declare which classes should be wired, and how the instances
should be accessed (see Scopes).

Classes that should be wired should be organized in "modules", which can be Scala `trait`s, `class`es or `object`s.
Multiple modules can be combined using inheritance; values from the inherited modules are also used for wiring.

MacWire can be in many cases a replacement for DI containers, offering greater control on when and how classes are
instantiated, typesafety and using only language (Scala) mechanisms.

Example usage:

    class DatabaseAccess()
    class SecurityFilter()
    class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
    class UserStatusReader(userFinder: UserFinder)

    trait UserModule {
        import com.softwaremill.macwire.MacwireMacros._

        lazy val theDatabaseAccess   = wire[DatabaseAccess]
        lazy val theSecurityFilter   = wire[SecurityFilter]
        lazy val theUserFinder       = wire[UserFinder]
        lazy val theUserStatusReader = wire[UserStatusReader]
    }

will generate:

    trait UserModule {
        lazy val theDatabaseAccess   = new DatabaseAccess()
        lazy val theSecurityFilter   = new SecurityFilter()
        lazy val theUserFinder       = new UserFinder(theDatabaseAccess, theSecurityFilter)
        lazy val theUserStatusReader = new theUserStatusReader(theUserFinder)
    }

For testing, just extend the base module and override any dependencies with mocks/stubs etc, e.g.:

    trait UserModuleForTests extends UserModule {
        override lazy val theDatabaseAccess = mockDatabaseAccess
        override lazy val theSecurityFilter = mockSecurityFilter
    }

The library has no dependencies, and itself is not a runtime dependency. It only needs to be available on the classpath
during compilation.

For more motivation behind the project see also these blogs:

* [Dependency injection with Scala macros: auto-wiring](http://www.warski.org/blog/2013/03/dependency-injection-with-scala-macros-auto-wiring/)

A similar project for Java is [Dagger](https://github.com/square/dagger).

How wiring works
----------------

For each constructor parameter of the given class, MacWire tries to find a value of the parameter's type in the
enclosing scope (trait/class/object):

* first it tries to find a unique value declared in the scope itself
* then it tries to find a unique value in parent scopes

A compile-time error occurs if:

* there are multiple values of a given type declared in the scope, or in parent scopes
* there is no value of a given type

The generated code is then once again type-checked by the Scala compiler.

`lazy val` vs. `val`
--------------------

It is safer to use `lazy val`s, as when using `val`, if a value is forward-referenced, it's value during initialization
will be `null`. With `lazy val` the correct order of initialization is resolved by Scala.

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

MacWire works with Scala 2.10+.

Limitations
-----------

When referencing wired values within the trait, e.g.:

    class A()
    class B(a: A)

    lazy val theA = wire[A]
     // reference to theA; if for some reason we need explicitly write the constructor call
    lazy val theB = new B(theA)

to avoid recursive type compiler errors, the referenced wired value needs a type ascription, e.g.:

    lazy val theA: A = wire[A]

Also, wiring will probably not work properly for traits and classes defined inside the containing trait/class, or in
super traits/classes.

Debugging
---------

The print debugging information on what MacWire does when looking for values, and what code is generated, set the
`macwire.debug` system property. E.g. with SBT, just add a `System.setProperty("macwire.debug", "")` line to your
build file.

Future development
------------------

* factories (defs with parameters)
* configuration values - by-name wiring
* inject a list of dependencies - of a given type