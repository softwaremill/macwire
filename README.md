MacWire
=======

MacWire generates `new` instance creation code of given classes, using values in the enclosing type for constructor
parameters, with the help of [Scala Macros](http://scalamacros.org/).

MacWire helps to implement the Dependency Injection (DI) pattern, by removing the need to write the
class-wiring code by hand. Instead, it is enough to declare which classes should be wired, and how the instances
should be accessed (see Scopes).

Classes that should be wired should be organized in "modules", which can be Scala `trait`s, `class`es or `object`s.
Multiple modules can be combined using inheritance; values from the inherited modules are also used for wiring.

MacWire can be in many cases a replacement for DI containers, offering greater control on when and how classes are
instantiated, typesafety and using only language (Scala) mechanisms.

Example usage:

````scala
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
````

will generate:

````scala
trait UserModule {
    lazy val theDatabaseAccess   = new DatabaseAccess()
    lazy val theSecurityFilter   = new SecurityFilter()
    lazy val theUserFinder       = new UserFinder(theDatabaseAccess, theSecurityFilter)
    lazy val theUserStatusReader = new UserStatusReader(theUserFinder)
}
````

For testing, just extend the base module and override any dependencies with mocks/stubs etc, e.g.:

````scala
trait UserModuleForTests extends UserModule {
    override lazy val theDatabaseAccess = mockDatabaseAccess
    override lazy val theSecurityFilter = mockSecurityFilter
}
````

Instead of importing the wire method in each module you can also extend com.softwaremill.macwire.Macwire trait.

The core library has no dependencies.

For more motivation behind the project see also these blogs:

* [Dependency injection with Scala macros: auto-wiring](http://www.warski.org/blog/2013/03/dependency-injection-with-scala-macros-auto-wiring/)
* [MacWire 0.1: Framework-less Dependency Injection with Scala Macros](http://www.warski.org/blog/2013/04/macwire-0-1-framework-less-dependency-injection-with-scala-macros/)
* [MacWire 0.2: Scopes are simple!](http://www.warski.org/blog/2013/04/macwire-0-2-scopes-are-simple/)
* [Implementing factories in Scala & MacWire 0.3](http://www.warski.org/blog/2013/06/implementing-factories-in-scala-macwire-0-3/)
* [Dependency Injection in Play! with MacWire](http://www.warski.org/blog/2013/08/dependency-injection-in-play-with-macwire/)

A similar project for Java is [Dagger](https://github.com/square/dagger).

How wiring works
----------------

For each constructor parameter of the given class, MacWire tries to find a value which is a subtype of the parameter's
type in the enclosing method and trait/class/object:

* first it tries to find a value declared in the enclosing method; if multiple values are found, a by name-match is attempted
* then it tries to find a unique value declared in the enclosing type
* then it tries to find a unique value in parent types (traits/classes)

Here value means either a `val` or a no-parameter `def`, as long as the return type matches.

A compile-time error occurs if:

* there are multiple values of a given type declared in the enclosing type, or in parent types
* there is no value of a given type

The generated code is then once again type-checked by the Scala compiler.

Factories
---------

A factory is simply a method. The constructor of the wired class can contain parameters both from
the factory (method) parameters, and from the enclosing/super type(s).

Unlike wiring in other places, if multiple values of the desired type are found, they are additionally filtered by-name.
In general this could give unpredictable results, but should be safe in the scope of a method.

For example:

````scala
class DatabaseAccess()
class TaxDeductionLibrary(databaseAccess: DatabaseAccess)
class TaxCalculator(taxBase: Double, taxFreeAmount: Double, taxDeductionLibrary: TaxDeductionLibrary)

trait TaxModule {
    import com.softwaremill.macwire.MacwireMacros._

    lazy val theDatabaseAccess      = wire[DatabaseAccess]
    lazy val theTaxDeductionLibrary = wire[TaxDeductionLibrary]
    def taxCalculator(taxBase: Double, taxFreeAmount: Double) = wire[TaxCalculator]
}
````

will generate:

````scala
trait TaxModule {
    lazy val theDatabaseAccess      = new DatabaseAccess()
    lazy val theTaxDeductionLibrary = new TaxDeductionLibrary(theDatabaseAccess)
    def taxCalculator(taxBase: Double, taxFreeAmount: Double) =
       new TaxCalculator(taxBase, taxFreeAmount, theTaxDeductionLibrary)
}
````

Limitations
-----------

When:

* referencing wired values within the trait/class/object
* using multiple modules in the same compilation unit
* using multiple modules with scopes

due to limitations of the current macros implementation in Scala (for more details see
[this discussion](https://groups.google.com/forum/?fromgroups=#!topic/scala-user/k_2KCvO5g04))
to avoid compilation errors it is recommended to add type ascriptions to the dependencies. This is a way of helping
the type-checker that is invoked by the macro to figure out the types of the values which
can be wired.

For example:

````scala
class A()
class B(a: A)

// note the explicit type. Without it wiring would fail with recursive type compile errors
lazy val theA: A = wire[A]
// reference to theA; if for some reason we need explicitly write the constructor call
lazy val theB = new B(theA)
````

This is a major inconvenience, but hopefully will get resolved once post-typer macros are introduced to the language.

Also, wiring will probably not work properly for traits and classes defined inside the containing trait/class, or in
super traits/classes.

Note that the type ascription may be a subtype of the wired type. This can be useful if you want to expose e.g. a trait
that the wired class extends, instead of the full implementation.

`lazy val` vs. `val`
--------------------

It is safer to use `lazy val`s, as when using `val`, if a value is forward-referenced, it's value during initialization
will be `null`. With `lazy val` the correct order of initialization is resolved by Scala.

Scopes
------

There are two "built-in" scopes, depending on how the dependency is defined:
* singleton: `lazy val` / `val`
* dependent - separate instance for each dependency usage: `def`

MacWire also supports user-defined scopes, which can be used to implement request or session scopes in web applications.
The `runtime` subproject defines a `Scope` trait, which has two methods:

* `apply`, to create a scoped value
* `get`, to get or create the current value from the scope

To define a dependency as scoped, we need a scope instance, e.g.:

```scala
trait WebModule {
   lazy val loggedInUser = session(new LoggedInUser)

   def session: Scope
}
```

With abstract scopes as above, it is possible to use no-op scopes for testing (`NoOpScope`).

There's an implementation of `Scope` targeted at classical synchronous frameworks, `ThreadLocalScope`. The apply method
of this scope creates a proxy (using [javassist](http://www.csg.is.titech.ac.jp/~chiba/javassist/)); the get method
stores the value in a thread local. The proxy should be defined as a `val` or `lazy val`.

In a web application, the scopes have to be associated and disassociated with storages.
This can be done for example in a servlet filter.
To implement a:

* request scope, we need a new empty storage for every request. The `associateWithEmptyStorage` is useful here
* session scope, the storage (a `Map`) should be stored in the `HttpSession`. The `associate(Map)` method is useful here

For example usage see the
[MacWire+Scalatra example](https://github.com/adamw/macwire/tree/master/examples/scalatra/src/main/scala/com/softwaremill/macwire/examples/scalatra)
sources.

You can run the example with `sbt examples-scalatra/run` and going to [http://localhost:8080](http://localhost:8080).

Note that the `runtime` subproject does not depend on MacWire core, and can be used stand-alone with manual wiring or any other
frameworks.

Instance maps
-------------

To integrate with some frameworks, e.g. [Play 2](http://www.playframework.com/), it is necessary to have a map of the
instances keyed by the instance class (`Map[Class[_], AnyRef]`). MacWire contains a utility macro, `valsByClass`, to
generate such a map, basing on the `val`s/`lazy val`s of the passed instance. E.g.:

````scala
object MyApp {
    lazy val theDatabaseAccess   = new DatabaseAccess()
    lazy val theSecurityFilter   = new SecurityFilter()
}

import MacwireMacros._
val instanceMap = valsByClass(MyApp)

require(instanceMap.size() == 2)
require(instanceMap(classOf[DatabaseAccess]) == MyApp.theDatabaseAccess)
````

The macro works with arbitrary objects and arbitrary `val` inside the objects: they can be hand-wired, wired using
`wired`, or a result of any computation.

To lookup instances by classes, superclasses and traits, you can use `InstanceLookup`. It takes an instance map, and
computes for which classes/traits what instances correspond (as opposed to the map creation, this is done at run-time).

For example:

````scala
trait DatabaseConnector
class MysqlDatabaseConnector extends DatabaseConnector

class MyApp {
    val databaseConnector = new MysqlDatabaseConnector()
}

import MacwireMacros._
val instanceMap = valsByClass(new MyApp)
val instanceLookup = InstanceLookup(instanceMap)

// Returns the mysql database connector, even though its type is MysqlDatabaseConnector, which is assignable to
// DatabaseConnector.
instanceLookup.lookup(classOf[DatabaseConnector])
````

Interceptors
------------

MacWire contains an implementation of interceptors, which can be applied to class instances in the modules.
Similarly to scopes, the `runtime` subproject defines an `Interceptor` trait, which has only one method: `apply`.
When applied to an instance, it should return an instance of the same class, but with the interceptor applied.

There are two implementations of the `Interceptor` trait provided:

* `NoOpInterceptor`: returns the given instance without changes
* `ProxyingInterceptor`: proxies the instance, and returns the proxy. A provided function is called
with information on the invocation

Interceptors can be abstract in modules. E.g.:

```scala
trait BusinessLogicModule {
   lazy val moneyTransferer = transactional(wire[MoneyTransferer])

   def transactional: Interceptor
}
```

During tests, you can then use the `NoOpInterceptor`. In production code or integration tests, you can specify a real
interceptor, either by extending the `ProxyingInterceptor` trait, or by passing a function to the
`ProxyingInterceptor` object:

```scala
object MyApplication extends BusinessLogicModule {
    lazy val tm = wire[TransactionManager]

    lazy val transactional = ProxyingInterceptor { ctx =>
        try {
            tm.begin()
            val result = ctx.proceed()
            tm.commit()

            result
        } catch {
            case e: Exception => tm.rollback()
        }
    }
}
```

The `ctx` is an instance of an `InvocationContext`, and contains information on the parameters passed to the method,
the method itself, and the target object. It also allows to proceed with the invocation with the same or changed
parameters.

For more general AOP, e.g. if you want to apply an interceptor to all methods matching a given pointcut expression,
you should use [AspectJ](http://eclipse.org/aspectj/) or an equivalent library. The interceptors that are implemented
in MacWire correspond to annotation-based interceptors in Java.

Installation, using with SBT
----------------------------

The jars are deployed to [Sonatype's OSS repository](https://oss.sonatype.org/content/repositories/snapshots/com/softwaremill/macwire/).
To use MacWire in your project, add a dependency:

````scala
libraryDependencies += "com.softwaremill.macwire" %% "macros" % "0.5"

libraryDependencies += "com.softwaremill.macwire" %% "runtime" % "0.5"
````

To use the snapshot version:

````scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "0.6-SNAPSHOT"

libraryDependencies += "com.softwaremill.macwire" %% "runtime" % "0.6-SNAPSHOT"
````

MacWire works with Scala 2.10+.

Debugging
---------

The print debugging information on what MacWire does when looking for values, and what code is generated, set the
`macwire.debug` system property. E.g. with SBT, just add a `System.setProperty("macwire.debug", "")` line to your
build file.

Future development
------------------

* relax type ascription requirements
* configuration values - by-name wiring
* inject a list of dependencies - of a given type
* qualifiers?


[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/adamw/macwire/trend.png)](https://bitdeli.com/free "Bitdeli Badge")

