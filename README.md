![MacWire](https://github.com/softwaremill/macwire/raw/master/banner.png)

Table of Contents
=================

- [Table of Contents](#table-of-contents)
- [MacWire](#macwire)
	- [How wiring works](#how-wiring-works)
	- [Factories](#factories)
	- [Factory methods](#factory-methods)
	- [`lazy val` vs. `val`](#lazy-val-vs-val)
	- [Composing modules](#composing-modules)
	- [Scopes](#scopes)
	- [Accessing wired instances dynamically](#accessing-wired-instances-dynamically)
	- [Interceptors](#interceptors)
	- [Qualifiers](#qualifiers)
	- [Multi Wiring (wireSet)](#multi-wiring-wireset)
	- [Limitations](#limitations)
	- [Akka integration](#akka-integration)
	- [Installation, using with SBT](#installation-using-with-sbt)
	- [Debugging](#debugging)
	- [Scala.js](#scalajs)
	- [Future development - vote!](#future-development---vote)
	- [Activators](#activators)
	- [Migrating from 1.x <a id="migrating"></a>](#migrating-from-1x-a-id%22migrating%22a)
	- [Play 2.4.x <a id="play24x"></a>](#play-24x-a-id%22play24x%22a)
	- [Play 2.5.x <a id="play25x"></a>](#play-25x-a-id%22play25x%22a)

MacWire
=======

[![Build Status](https://travis-ci.org/adamw/macwire.svg?branch=master)](https://travis-ci.org/adamw/macwire)
[![Join the chat at https://gitter.im/adamw/macwire](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/adamw/macwire?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.macwire/macros_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.macwire/macros_2.11)

MacWire generates `new` instance creation code of given classes, using values in the enclosing type for constructor
parameters, with the help of [Scala Macros](http://scalamacros.org/).

For a general introduction to DI in Scala, take a look at the [Guide to DI in Scala](http://di-in-scala.github.io/),
which also features MacWire.

MacWire helps to implement the Dependency Injection (DI) pattern, by removing the need to write the
class-wiring code by hand. Instead, it is enough to declare which classes should be wired, and how the instances
should be accessed (see Scopes).

Classes to be wired should be organized in "modules", which can be Scala `trait`s, `class`es or `object`s.
Multiple modules can be combined using inheritance or composition; values from the inherited/nested modules are also
used for wiring.

MacWire can be in many cases a replacement for DI containers, offering greater control on when and how classes are
instantiated, typesafety and using only language (Scala) mechanisms.

Example usage:

````scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

trait UserModule {
    import com.softwaremill.macwire._

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

The core library has no dependencies.

For more motivation behind the project see also these blogs:

* [Dependency injection with Scala macros: auto-wiring](http://www.warski.org/blog/2013/03/dependency-injection-with-scala-macros-auto-wiring/)
* [MacWire 0.1: Framework-less Dependency Injection with Scala Macros](http://www.warski.org/blog/2013/04/macwire-0-1-framework-less-dependency-injection-with-scala-macros/)
* [MacWire 0.2: Scopes are simple!](http://www.warski.org/blog/2013/04/macwire-0-2-scopes-are-simple/)
* [Implementing factories in Scala & MacWire 0.3](http://www.warski.org/blog/2013/06/implementing-factories-in-scala-macwire-0-3/)
* [Dependency Injection in Play! with MacWire](http://www.warski.org/blog/2013/08/dependency-injection-in-play-with-macwire/)
* [MacWire 0.5: Interceptors](http://www.warski.org/blog/2013/10/macwire-0-5-interceptors/)
* [Using Scala traits as modules, or the "Thin Cake" Pattern](http://www.warski.org/blog/2014/02/using-scala-traits-as-modules-or-the-thin-cake-pattern/)

You can also try MacWire through [Typesafe Activator](http://typesafe.com/activator/template/macwire-activator).

A similar project for Java is [Dagger](https://github.com/square/dagger).

How wiring works
----------------

For each constructor parameter of the given class, MacWire tries to find a value [conforming](http://www.scala-lang.org/files/archive/spec/2.11/03-types.html#conformance) to the parameter's
type in the enclosing method and trait/class/object:

* first it tries to find a unique value declared as a value in the current block, argument of enclosing methods
and anonymous functions.
* then it tries to find a unique value declared or imported in the enclosing type
* then it tries to find a unique value in parent types (traits/classes)
* if the parameter is marked as implicit, it is ignored by MacWire and handled by the normal implicit resolution mechanism

Here value means either a `val` or a no-parameter `def`, as long as the return type matches.

A compile-time error occurs if:

* there are multiple values of a given type declared in the enclosing block/method/function's arguments list, enclosing type or its parents.
* parameter is marked as implicit and implicit lookup fails to find a value
* there is no value of a given type

The generated code is then once again type-checked by the Scala compiler.

Factories
---------

A factory is simply a method. The constructor of the wired class can contain parameters both from
the factory (method) parameters, and from the enclosing/super type(s).

For example:

````scala
class DatabaseAccess()
class TaxDeductionLibrary(databaseAccess: DatabaseAccess)
class TaxCalculator(taxBase: Double, taxDeductionLibrary: TaxDeductionLibrary)

trait TaxModule {
    import com.softwaremill.macwire._

    lazy val theDatabaseAccess      = wire[DatabaseAccess]
    lazy val theTaxDeductionLibrary = wire[TaxDeductionLibrary]
    def taxCalculator(taxBase: Double) = wire[TaxCalculator]
    // or: lazy val taxCalculator = (taxBase: Double) => wire[TaxCalculator]
}
````

will generate:

````scala
trait TaxModule {
    lazy val theDatabaseAccess      = new DatabaseAccess()
    lazy val theTaxDeductionLibrary = new TaxDeductionLibrary(theDatabaseAccess)
    def taxCalculator(taxBase: Double) =
       new TaxCalculator(taxBase, theTaxDeductionLibrary)
}
````

Factory methods
---------------

You can also wire an object using a factory method, instead of a constructor. For that, use `wireWith` instead of
`wire`. For example:

````scala
class A()

class C(a: A, specialValue: Int)
object C {
  def create(a: A) = new C(a, 42)
}

trait MyModule {
  lazy val a = wire[A]
  lazy val c = wireWith(C.create _)
}
````

`lazy val` vs. `val`
--------------------

It is safer to use `lazy val`s, as when using `val`, if a value is forward-referenced, it's value during initialization
will be `null`. With `lazy val` the correct order of initialization is resolved by Scala.

Composing modules
-----------------

Modules (traits or classes containing parts of the object graph) can be combined using inheritance or composition.
The inheritance case is straightforward, as `wire` simply looks for values in parent traits/classes. With composition,
you need to tell MacWire that it should look inside the nested modules.

To do that, you can use imports:

````scala
class FacebookAccess(userFind: UserFinder)

class UserModule {
  lazy val userFinder = ... // as before
}

class SocialModule(userModule: UserModule) {
  import userModule._

  lazy val facebookAccess = wire[FacebookAccess]
}
````

Or, if you are using that pattern a lot, you can annotate your modules using `@Module`, and they will be used when
searching for values automatically:

````scala
class FacebookAccess(userFind: UserFinder)

@Module
class UserModule { ... } // as before

class SocialModule(userModule: UserModule) {
  lazy val facebookAccess = wire[FacebookAccess]
}
````

**Warning**: the `@Module` annotation is an experimental feature, if you have any feedback regarding its usage, let
us know!

Scopes
------

There are two "built-in" scopes, depending on how the dependency is defined:
* singleton: `lazy val` / `val`
* dependent - separate instance for each dependency usage: `def`

MacWire also supports user-defined scopes, which can be used to implement request or session scopes in web applications.
The `proxy` subproject defines a `Scope` trait, which has two methods:

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

Note that the `proxy` subproject does not depend on MacWire core, and can be used stand-alone with manual wiring or any other
frameworks.

Accessing wired instances dynamically
-------------------------------------

To integrate with some frameworks (e.g. [Play 2](http://www.playframework.com/)) or to create instances of classes
which names are only known at run-time (e.g. plugins) it is necessary to access the wired instances dynamically.
MacWire contains a utility class in the `util` subproject, `Wired`, to support such functionality.

An instance of `Wired` can be obtained using the `wiredInModule` macro, given an instance of a module containing the
wired object graph. Any `vals`, `lazy val`s and parameter-less `def`s (factories) from the module which are references
will be available in the `Wired` instance.

The object graph in the module can be hand-wired, wired using `wire`, or a result of any computation.

`Wired` has two basic functionalities: looking up an instance by its class (or trait it implements), and instantiating
new objects using the available dependencies. You can also extend `Wired` with new instances/instance factories.

For example:

````scala
// 1. Defining the object graph and the module
trait DatabaseConnector
class MysqlDatabaseConnector extends DatabaseConnector

class MyApp {
    def securityFilter = new SecurityFilter()
    val databaseConnector = new MysqlDatabaseConnector()
}

// 2. Creating a Wired instance
import com.softwaremill.macwire._
val wired = wiredInModule(new MyApp)

// 3. Dynamic lookup of instances
wired.lookup(classOf[SecurityFilter])

// Returns the mysql database connector, even though its type is MysqlDatabaseConnector, which is
// assignable to DatabaseConnector.
wired.lookup(classOf[DatabaseConnector])

// 4. Instantiation using the available dependencies
{
    package com.softwaremill
    class AuthenticationPlugin(databaseConnector: DatabaseConnector)
}

// Creates a new instance of the given class using the dependencies available in MyApp
wired.wireClassInstanceByName("com.softwaremill.AuthenticationPlugin")
````

Interceptors
------------

MacWire contains an implementation of interceptors, which can be applied to class instances in the modules.
Similarly to scopes, the `proxy` subproject defines an `Interceptor` trait, which has only one method: `apply`.
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

Qualifiers
----------

Sometimes you have multiple objects of the same type that you want to use during wiring. Macwire needs to have some
way of telling the instances apart. As with other things, the answer is: types! Even when not using `wire`, it may
be useful to give the instances distinct types, to get compile-time checking.

For that purpose Macwire includes support for tagging via [scala-common](https://github.com/softwaremill/scala-common),
which lets you attach tags to instances to qualify them. This
is a compile-time only operation, and doesn't affect the runtime. The tags are derived from
[Miles Sabin's gist](https://gist.github.com/milessabin/89c9b47a91017973a35f).

To bring the tagging into scope, import `com.softwaremill.tagging._`.

Using tagging has two sides. In the constructor, when declaring a dependency, you need to declare what tag it needs
to have. You can do this with the `_ @@ _` type constructor, or if you prefer another syntax `Tagged[_, _]`. The first
type parameter is the type of the dependency, the second is a tag.

The tag can be any type, but usually it is just an empty marker trait.

When defining the available instances, you need to specify which instance has which tag. This can be done with the
`taggedWith[_]` method, which returns a tagged instance (`A.taggedWith[T]: A @@ T`). Tagged instances can be used
as regular ones, without any constraints.

The `wire` macro does not contain any special support for tagging, everything is handled by subtyping. For example:

````scala
class Berry()
trait Black
trait Blue

case class Basket(blueberry: Berry @@ Blue, blackberry: Berry @@ Black)

lazy val blueberry = wire[Berry].taggedWith[Blue]
lazy val blackberry = wire[Berry].taggedWith[Black]
lazy val basket = wire[Basket]
````

Multiple tags can be combined using the `andTaggedWith` method. E.g. if we had a berry that is both blue and black:

````scala
lazy val blackblueberry = wire[Berry].taggedWith[Black].andTaggedWith[Blue]
````

The resulting value has type `Berry @ (Black with Blue)` and can be used both as a blackberry and as a blueberry.

Multi Wiring (wireSet)
----------

Using `wireSet` you can obtain a set of multiple instances of the same type. This is done without constructing the set explicitly. All instances of the same type which are found by MacWire are used to construct the set.

Consider the below example. Let's suppose that you want to create a `RockBand(musicians: Set[Musician])` object. It's easy to do so using the `wireSet` functionality:

```scala
trait Musician
class RockBand(musicians: Set[Musician])

trait RockBandModule {
  lazy val singer    = new Musician {}
  lazy val guitarist = new Musician {}
  lazy val drummer   = new Musician {}
  lazy val bassist   = new Musician {}

  lazy val musicians = wireSet[Musician] // all above musicians will be wired together
                                         // musicians has type Set[Musician]

  lazy val rockBand  = wire[RockBand]
}
```

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

This is an inconvenience, but hopefully will get resolved once post-typer macros are introduced to the language.

Also, wiring will probably not work properly for traits and classes defined inside the containing trait/class, or in
super traits/classes.

Note that the type ascription may be a subtype of the wired type. This can be useful if you want to expose e.g. a trait
that the wired class extends, instead of the full implementation.

Akka integration
----------------

Macwire provides wiring suport for [akka](http://akka.io) through the `macrosAkka` module.
[Here](https://github.com/adamw/macwire/blob/master/macrosAkkaTests/src/test/scala/com/softwaremill/macwire/akkasupport/demo/Demo.scala)
you can find example code. The module adds three macros `wireAnonymousActor[A]`, `wireActor[A]` and `wireProps[A]`
 which help create instances of `akka.actor.ActorRef` and `akka.actor.Props`.

These macros require an `ActoRefFactory` (`ActorSystem` or `Actor.context`) to be in scope as a dependency.
If actor's primary constructor has dependencies - they are required to be in scope as well.

Example usage:

```scala
import akka.actor.{Actor, ActorRef, ActorSystem}

class DatabaseAccess()
class SecurityFilter()
class UserFinderActor(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter) extends Actor {
  override def receive: Receive = {
    case m => // ...
  }
}

import com.softwaremill.macwire._
import com.softwaremill.macwire.akkasupport._

val theDatabaseAccess = wire[DatabaseAccess] //1st dependency for UserFinderActor
                                             //it compiles to: val theDatabaseAccess = new DatabaseAccess

val theSecurityFilter = wire[SecurityFilter] //2nd dependency for UserFinderActor
                                             //it compiles to: val theSecurityFilter = new SecurityFilter

val system = ActorSystem("actor-system") //there must be instance of ActoRefFactory in scope

val theUserFinder = wireActor[UserFinderActor]("userFinder")
//this compiles to:
//lazy val theUserFinder = system.actorOf(Props(classOf[UserFinderActor], theDatabaseAccess, theSecurityFilter), "userFinder")
```

In order to make it working all dependencies created `Actor`'s (`UserFinderActor`'s) primary constructor and
instance of the `akka.actor.ActorRefFactory` must be in scope. In above example this is all true. Dependencies
of the `UserFinderActor` are `DatabaseAccess` and `SecurityFilter` and they are in scope.
The `ActorRefFactory` is in scope as well because `ActorSystem` which is subtype of it is there.

Creating actor within another actor is even simpler than in first example because we don't need to have `ActorSystem` in scope.
The `ActorRefFactory` is here because `Actor.context` is subtype of it. Let's see this in action:

```scala
class UserStatusReaderActor(theDatabaseAccess: DatabaseAccess) extends Actor {
  val theSecurityFilter = wire[SecurityFilter]

  val userFinder = wireActor[UserFinderActor]("userFinder")
  //this compiles to:
  //val userFinder = context.actorOf(Props(classOf[UserFinderActor], theDatabaseAccess, theSecurityFilter), "userFinder")

  override def receive = ...
}
```

The difference is that previously macro expanded into `system.actorOf(...)`
and when inside another actor it expanded into `context.actoOf(...)`.


It's possible to create anonymous actors. `wireAnonymousActor` is for it:

```scala
val userFinder = wireAnonymousActor[UserFinderActor]
//this compiles to:
//val userFinder = context.actorOf(Props(classOf[UserFinderActor], theDatabaseAccess, theSecurityFilter))
```

How about creating `akka.actor.Props`? It's there and can be achieved by calling `wireProps[A]`.
Wiring only `Props` can be handy when it's required to setup the `Props` before passing them to the `actorOf(...)` method.

Let's say we want to create some actor with router. It can be done as below:
```scala
val userFinderProps = wireProps[UserFinderActor] //create Props
  //This compiles to: Props(classOf[UserFinderActor], theDatabaseAccess, theSecurityFilter)
  .withRouter(RoundRobinPool(4)) //change it according requirements
val userFinderActor = system.actorOf(userFinderProps, "userFinder")  //create the actor
```

How about creating actors which depends on `ActorRef`s? The simplest way is to
pass them as arguments to the constructor. But how to distinguish two `actorRef`s representing two different actors?
They have the same type though.

```scala
class DatabaseAccessActor extends Actor { ... }
class SecurityFilterActor extends Actor { ... }
val db: ActorRef = wireActor[DatabaseAccessActor]("db")
val filter: ActorRef = wireActor[SecurityFilterActor]("filter")
class UserFinderActor(databaseAccess: ActorRef, securityFilter: ActorRef) extends Actor {...}
//val userFinder = wireActor[UserFinderActor] wont work here
```

We can't just call `wireActor[UserFinderActor]` because it's not obvious which instance of ActorRef
is for `databaseAccess` and which are for `securityFilter`. They are both of the same type - `ActorRef`.

The solution for it is to use earlier described [qualifiers](#qualifiers).
In above example solution for wiring may look like this:

```scala
sealed trait DatabaseAccess //marker type
class DatabaseAccessActor extends Actor { ... }
sealed trait SecurityFilter //marker type
class SecurityFilterActor extends Actor { ... }

val db: ActorRef @@ DatabaseAccess = wireActor[DatabaseAccessActor]("db").taggedWith[DatabaseAccess]
val filter: ActorRef @@ SecurityFilter = wireActor[SecurityFilterActor]("filter").taggedWith[SecurityFilter]

class UserFinderActor(databaseAccess: ActorRef @@ DatabaseAccess, securityFilter: ActorRef @@ SecurityFilter) extends Actor {...}

val userFinder = wireActor[UserFinderActor]
```


Installation, using with SBT
----------------------------

The jars are deployed to [Sonatype's OSS repository](https://oss.sonatype.org/content/repositories/snapshots/com/softwaremill/macwire/).
To use MacWire in your project, add a dependency:

````scala
libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.3.4" % "provided"

libraryDependencies += "com.softwaremill.macwire" %% "macrosakka" % "2.3.4" % "provided"

libraryDependencies += "com.softwaremill.macwire" %% "util" % "2.3.4"

libraryDependencies += "com.softwaremill.macwire" %% "proxy" % "2.3.4"
````

The `macros` subproject contains only code which is used at compile-time, hence the `provided` scope.

The `util` subproject contains tagging, `Wired` and the `@Module` annotation; if you don't use these features, you don't
need to include this dependency.

The `proxy` subproject contains interceptors and scopes, and has a dependency on `javassist`.

To use the snapshot version:

````scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.3.4-SNAPSHOT" % "provided"

libraryDependencies += "com.softwaremill.macwire" %% "util" % "2.3.4-SNAPSHOT"
````

Currently 2.x supports Scala 2.11 and 2.12.

Older 1.x release for Scala 2.10 and 2.11:

````scala
libraryDependencies += "com.softwaremill.macwire" %% "macros" % "1.0.7"

libraryDependencies += "com.softwaremill.macwire" %% "runtime" % "1.0.7"
````

Debugging
---------

To print debugging information on what MacWire does when looking for values, and what code is generated, set the
`macwire.debug` system property. E.g. with SBT, just add a `System.setProperty("macwire.debug", "")` line to your
build file.

Scala.js
--------

Macwire also works with [Scala.js](http://www.scala-js.org/). For an example, see here:
[Macwire+Scala.js example](https://github.com/adamw/macwire/tree/master/examples/scalajs).

Future development - vote!
--------------------------

Take a look at the [available issues](https://github.com/adamw/macwire/issues). If you'd like to see one developed
please vote on it. Or maybe you'll attempt to create a pull request?

Activators
----------

There are two Typesafe Activators which can help you to get started with Scala, Dependency Injection and Macwire:

* [No-framework Dependency Injection with MacWire and Akka Activator](https://typesafe.com/activator/template/macwire-akka-activator)
* [No-framework Dependency Injection with MacWire and Play Activator](https://typesafe.com/activator/template/macwire-activator)

Migrating from 1.x <a id="migrating"></a>
------------------

* changed how code is split across modules. You'll need to depend on `util` to get tagging & `Wired`, and `proxy`
to get interceptors and scopes
* tagging moved to a separate package. If you use tagging, you'll need to import `com.softwaremill.tagging._`
* removed `wireImplicit`
* implicit parameters aren't handled by `wire` at all (they used to be subject to the same lookup procedure as normal
parameters + implicit lookup)

Play 2.4.x <a id="play24x"></a>
--------

In Play 2.4.x, you can no longer use getControllerInstance in GlobalSettings for injection. Play has a new pattern for injecting controllers. You must extend ApplicationLoader, from there you can mix in your modules.

````scala
import controllers.{Application, Assets}
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes
import com.softwaremill.macwire._

class AppApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {

    // make sure logging is configured
    Logger.configure(context.environment)

    (new BuiltInComponentsFromContext(context) with AppComponents).application
  }
}

trait AppComponents extends BuiltInComponents with AppModule {
  lazy val assets: Assets = wire[Assets]
  lazy val prefix: String = "/"
  lazy val router: Router = wire[Routes]
}

trait AppModule {
  // Define your dependencies and controllers
  lazy val applicationController = wire[Application]
}
````

In application.conf, add the reference to the ApplicationLoader.

````
play.application.loader = "AppApplicationLoader"
````


For more information and to see the sample project, go to [examples/play24](https://github.com/adamw/macwire/tree/master/examples/play24)

Reference Play docs for more information:

* [ScalaCompileTimeDependencyInjection](https://www.playframework.com/documentation/2.4.x/ScalaCompileTimeDependencyInjection)


Play 2.5.x <a id="play25x"></a>
--------

For Play 2.5.x, you must do the same as for Play 2.4.x, except the `Logger` configuration.

````scala
import play.api.LoggerConfigurator
class AppApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {

    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    // ... do the same as for Play 2.4.x
  }
}
````

There's also a [G8 template available](https://github.com/lloydmeta/ctdi-play.g8) which can be used with `sbt new`.

## Commercial Support

We offer commercial support for MacWire and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2013-2019 SoftwareMill [https://softwaremill.com](https://softwaremill.com).

