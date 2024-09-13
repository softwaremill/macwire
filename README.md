![MacWire](https://github.com/softwaremill/macwire/raw/master/banner.png)

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/macwire)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.macwire/macros_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.macwire/macros_2.13)

Zero-cost, compile-time, type-safe dependency injection library.

The core of MacWire is a set of macros, that is code which runs at compile-time. The macros generate the `new` instance
creation code automatically, allowing you to avoid a ton of boilerplate code, while maintaining type-safety.

The core library has no dependencies, and incurs no run-time overhead. The main mechanism of defining dependencies of a
class are plain constructors or `apply` methods in companion objects. MacWire doesn't impact the code of your classes 
in any way, there are no annotations that you have to use or conventions to follow.

There's a couple of wiring variants that you can choose from:

* `autowire` create an instance of the given type, using the provided dependencies. Any missing dependencies are created
  using constructors/`apply` methods.
* `wire` create an instance of the given type, using dependencies from the context, within which it is called. 
  Dependencies are looked up in the enclosing trait/class/object and parents (via inheritance).
* `wireRec` is a variant of `wire`, which creates missing dependencies using constructors.

In other words, `autowire` is context-free, while the `wire` family of macros is context-dependent.

MacWire is available for Scala 2.12, 2.13 and 3 on the JVM, JS and Native platforms. Not all functionalities are 
available for each Scala version.

To use, add the following dependency:

```
// sbt
"com.softwaremill.macwire" %% "macros" % "2.6.0" % "provided"

// scala-cli
//> using dep com.softwaremill.macwire::macros:2.6.0
```

# Table of Contents

- [Table of Contents](#table-of-contents)
- [autowire](#autowire)
  - [Providing instantiated dependencies](#providing-instantiated-dependencies)
  - [Using factories](#using-factories)
  - [Specifying implementations to use](#specifying-implementations-to-use)
  - [Using dependencies contained in objects](#using-dependencies-contained-in-objects)
  - [Errors](#errors)
- [wire](#wire)
	- [How wiring works](#how-wiring-works)
	- [Factories](#factories)
	- [Factory methods](#factory-methods)
	- [`lazy val` vs. `val`](#lazy-val-vs-val)
	- [Recursive wiring](#recursive-wiring)
	- [Composing modules](#composing-modules)
	- [Scopes](#scopes)
	- [Accessing wired instances dynamically](#accessing-wired-instances-dynamically)
  - [Limitations](#limitations)
  - [Akka integration](#akka-integration)
  - [Multi Wiring (wireSet)](#multi-wiring-wireset)
- [Autowire for cats-effect](#autowire-for-cats-effect)  
- [Interceptors](#interceptors)
- [Qualifiers](#qualifiers)
- [Development](#development)	
	- [Debugging](#debugging)
	- [Future development - vote!](#future-development---vote)
- [Platform and version-specifics](#platform-and-version-specifics)  
	- [Scala.js](#scalajs)
  - [Scala3 support](#scala3-support)
- [Other](#other)
  - [Commerical support](#commercial-support)
  - [Copyright](#copyright)

# autowire

![Scala 3](https://img.shields.io/badge/Scala%203-8A2BE2)
![direct-style](https://img.shields.io/badge/direct--style-228B22)

Autowire generates the code needed to instantiate the given type. To create the instance, the public primary 
constructor is used, or if one is absent - the `apply` method from the companion object. Any dependencies are
created recursively. For example, given the following classes:

```scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)
```

invoking `autowire` as below:

```scala
import com.softwaremill.macwire.*
val userStatusReader = autowire[UserStatusReader]()
```

generates following code:

```scala
val userStatusReader =
  val wiredDatabaseAccess   = new DatabaseAccess()
  val wiredSecurityFilter   = new SecurityFilter()
  val wiredUserFinder       = new UserFinder(wiredDatabaseAccess, wiredSecurityFilter)
  val wiredUserStatusReader = new UserStatusReader(wiredUserFinder)
  wiredUserStatusReader
```

`autowire` accepts an arbitrary number of parameters, which specify how to lookup or create dependencies, instead
of the default construct/`apply` mechanism. These are described in detail below. Each such parameter might be:

* an instance to use
* a function to create an instance
* a class to instantiate to provide a dependency for the types it implements (provided as: `classOf[SomeType]`)
* a `membersOf(instance)` call, to use the members of the given instance as dependencies

`autowire` is context-free: its result does not depend on the environment, within which it is called (except for
implicit parameters, which are looked up using the usual mechanism). It only depends on the type that is specified
for wiring, and any parameters that are passed.

## Providing instantiated dependencies

In some cases it's necessary to instantiate a dependency by hand, e.g. to initialise it with some configuration,
or to manage its lifecycle. In such cases, dependencies can be provided as parameters to the `autowire` invocation.
They will be used whenever a value of the given type is needed.

As an example, consider a `DataSource`, which needs to be configured with a JDBC connection string, and has a 
managed life-cycle:

```scala
import java.io.Closeable

class DataSource(jdbcConn: String) extends Closeable { def close() = () }
class DatabaseAccess(ds: DataSource)
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)
```

We can provide a `DataSource` instance to be used by `autowire`:

```scala
import com.softwaremill.macwire.*
import scala.util.Using

Using.resource(DataSource("jdbc:h2:~/test")): ds =>
  autowire[UserStatusReader](ds)
```

## Using factories

In addition to instances, which should be used by `autowire`, it's also possible to provide factory methods. They will
be used to create instances of types which is the result of the provided function, using the dependencies which are the
function's parameters. Any dependencies are recursively created. 

For example, we can provide a custom way to create a `UserFinder`:

```scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter, 
  adminOnly: Boolean)
class UserStatusReader(userFinder: UserFinder)

autowire[UserStatusReader](UserFinder(_, _, adminOnly = true))
```

## Specifying implementations to use

You can specify which classes to use to create instances. This is useful when the dependencies are expressed for 
example using `trait`s, not the concrete types. Such a dependency can be expressed by providing a `classOf[]`
parameter to `autowire`:

```scala
trait DatabaseAccess
class DatabaseAccessImpl() extends DatabaseAccess

class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

autowire[UserStatusReader](classOf[DatabaseAccessImpl])
```

Without the `classOf[]`, MacWire wouldn't know how to create an instance implementing `DatabaseAccess`.

## Using dependencies contained in objects

Finally, it's possible to use the members of a given instance as dependencies. Simply pass a `memberOf(someInstance)` 
as a parameter to `autowire`.

## Errors

`autowire` reports an error when:

* a dependency can't be created (e.g. there are no public constructors / `apply` methods)
* there are circular dependencies
* the provided dependencies contain a duplicate
* a provided dependency is not used
* a primitive or `String` type is used as a dependency (instead, use e.g. an opaque type)

Each error contains the wiring path. For example, below there's no public constructor for `DatabaseAccess`, which
results in a compile-time error:

```scala
class DatabaseAccess private ()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

autowire[UserStatusReader]()

// compile-time error:
// cannot find a provided dependency, constructor or apply method for: DatabaseAccess;
// wiring path: UserStatusReader -> UserFinder -> DatabaseAccess
```

# wire

![Scala 2](https://img.shields.io/badge/Scala%202-8A2BE2)
![Scala 3](https://img.shields.io/badge/Scala%203-8A2BE2)
![direct-style](https://img.shields.io/badge/direct--style-228B22)

MacWire generates `new` instance creation code of given classes, using values in the enclosing type for constructor
parameters, with the help of Scala Macros.

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

```scala
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
```

will generate:

```scala
trait UserModule {
  lazy val theDatabaseAccess   = new DatabaseAccess()
  lazy val theSecurityFilter   = new SecurityFilter()
  lazy val theUserFinder       = new UserFinder(theDatabaseAccess, theSecurityFilter)
  lazy val theUserStatusReader = new UserStatusReader(theUserFinder)
}
```

For testing, just extend the base module and override any dependencies with mocks/stubs etc, e.g.:

```scala
trait UserModuleForTests extends UserModule {
  override lazy val theDatabaseAccess = mockDatabaseAccess
  override lazy val theSecurityFilter = mockSecurityFilter
}
```

The core library has no dependencies.

For more motivation behind the project see also these blogs:

* [Dependency injection with Scala macros: auto-wiring](http://www.warski.org/blog/2013/03/dependency-injection-with-scala-macros-auto-wiring/)
* [MacWire 0.1: Framework-less Dependency Injection with Scala Macros](http://www.warski.org/blog/2013/04/macwire-0-1-framework-less-dependency-injection-with-scala-macros/)
* [MacWire 0.2: Scopes are simple!](http://www.warski.org/blog/2013/04/macwire-0-2-scopes-are-simple/)
* [Implementing factories in Scala & MacWire 0.3](http://www.warski.org/blog/2013/06/implementing-factories-in-scala-macwire-0-3/)
* [Dependency Injection in Play! with MacWire](http://www.warski.org/blog/2013/08/dependency-injection-in-play-with-macwire/)
* [MacWire 0.5: Interceptors](http://www.warski.org/blog/2013/10/macwire-0-5-interceptors/)
* [Using Scala traits as modules, or the "Thin Cake" Pattern](http://www.warski.org/blog/2014/02/using-scala-traits-as-modules-or-the-thin-cake-pattern/)

## How wiring works

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

## Factories

A factory is simply a method. The constructor of the wired class can contain parameters both from
the factory (method) parameters, and from the enclosing/super type(s).

For example:

```scala
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
```

will generate:

```scala
trait TaxModule {
  lazy val theDatabaseAccess      = new DatabaseAccess()
  lazy val theTaxDeductionLibrary = new TaxDeductionLibrary(theDatabaseAccess)
  def taxCalculator(taxBase: Double) =
    new TaxCalculator(taxBase, theTaxDeductionLibrary)
}
```

## Factory methods

You can also wire an object using a factory method, instead of a constructor. For that, use `wireWith` instead of
`wire`. For example:

```scala
class A()

class C(a: A, specialValue: Int)
object C {
  def create(a: A) = new C(a, 42)
}

trait MyModule {
  lazy val a = wire[A]
  lazy val c = wireWith(C.create _)
}
```

## `lazy val` vs. `val`

It is safer to use `lazy val`s, as when using `val`, if a value is forward-referenced, it's value during initialization
will be `null`. With `lazy val` the correct order of initialization is resolved by Scala.

## Recursive wiring

When using `wire` and a value for a parameter can't be found, an error is reported. `wireRec` takes a different 
approach - it tries to recursively create an instance, using normal wiring rules. This allows to explicitly wire
only those objects, which are referenced from the code, skipping helper or internal ones.

The previous example becomes:

```scala
class DatabaseAccess()
class SecurityFilter()
class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(userFinder: UserFinder)

trait UserModule {
  import com.softwaremill.macwire._

  lazy val theUserStatusReader = wireRec[UserStatusReader]
}
```

and will generate:

```scala
trait UserModule {
  lazy val theUserStatusReader = new UserStatusReader(
		new UserFinder(new DatabaseAccess(), new SecurityFilter()))
}
```

This feature is inspired by @yakivy's work on [jam](https://github.com/yakivy/jam).

## Composing modules

Modules (traits or classes containing parts of the object graph) can be combined using inheritance or composition.
The inheritance case is straightforward, as `wire` simply looks for values in parent traits/classes. With composition,
you need to tell MacWire that it should look inside the nested modules.

To do that, you can use imports:

```scala
class FacebookAccess(userFind: UserFinder)

class UserModule {
  lazy val userFinder = ... // as before
}

class SocialModule(userModule: UserModule) {
  import userModule._

  lazy val facebookAccess = wire[FacebookAccess]
}
```

### Avoiding imports

Dependency:

```scala
"com.softwaremill.macwire" %% "util" % "2.6.0"
```

If you are using that pattern a lot, you can annotate your modules using `@Module`, and they will be used when
searching for values automatically:

```scala
class FacebookAccess(userFind: UserFinder)

@Module
class UserModule { ... } // as before

class SocialModule(userModule: UserModule) {
  lazy val facebookAccess = wire[FacebookAccess]
}
```

## Scopes

Dependency:

```
"com.softwaremill.macwire" %% "proxy" % "2.6.0"
```

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

## Accessing wired instances dynamically

Dependency:

```scala
"com.softwaremill.macwire" %% "util" % "2.6.0"
```

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

```scala
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

// Returns the mysql database connector, even though its type is MysqlDatabaseConnector, 
// which is assignable to DatabaseConnector.
wired.lookup(classOf[DatabaseConnector])

// 4. Instantiation using the available dependencies
{
  package com.softwaremill
  class AuthenticationPlugin(databaseConnector: DatabaseConnector)
}

// Creates a new instance of the given class using the dependencies available in MyApp
wired.wireClassInstanceByName("com.softwaremill.AuthenticationPlugin")
```

## Limitations

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

```scala
class A()
class B(a: A)

// note the explicit type. Without it wiring would fail with recursive type compile errors
lazy val theA: A = wire[A]
// reference to theA; if for some reason we need explicitly write the constructor call
lazy val theB = new B(theA)
```

This is an inconvenience, but hopefully will get resolved once post-typer macros are introduced to the language.

Also, wiring will probably not work properly for traits and classes defined inside the containing trait/class, or in
super traits/classes.

Note that the type ascription may be a subtype of the wired type. This can be useful if you want to expose e.g. a trait
that the wired class extends, instead of the full implementation.

## Akka integration

Dependency:

```scala
"com.softwaremill.macwire" %% "macrosakka" % "2.6.0" % "provided"
```

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
and when inside another actor it expanded into `context.actorOf(...)`.


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

It is also possible to wire an actor using a factory function.
For that, the module provides three additional macros `wireAnonymousActorWith`, `wireActorWith` and `wirePropsWith`.
Their usage is similar to `wireWith` (see [Factory methods](#factory-methods)).
For example:

```scala
class UserFinderActor(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter) extends Actor { ... }

object UserFinderActor {
  def get(databaseAccess: DatabaseAccess) = new UserFinderActor(databaseAccess, new SimpleSecurityFilter())
}

val theUserFinder = wireActorWith(UserFinderActor.get _)("userFinder")
```

## Multi Wiring (wireSet)

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

# Autowire for cats-effect

![Scala 2](https://img.shields.io/badge/Scala%202-8A2BE2)
![cats-effect](https://img.shields.io/badge/cats--effect-228B22)

**Warning**: `autowire` is an experimental feature, if you have any feedback regarding its usage, let us know! Future releases might break source/binary compatibility. It is available for Scala 2 only for now.

Dependency: `"com.softwaremill.macwire" %% "macrosautocats" % "2.6.0"`

In case you need to build an instance from some particular instances and factory methods you can leverage `autowire`. This feature is intended to integrate with effect-management libraries (currently we support [cats-effect](https://github.com/typelevel/cats-effect)).

`autowire` takes as an argument a list of arguments which may contain:

* values (e.g. `new A()`)
* factory methods (e.g. `C.create _`)
* factory methods that return `cats.effect.Resource` or `cats.effect.IO` (e.g. `C.createIO _`)
* `cats.effect.Resource` (e.g. `cats.effect.Resource[IO].pure(new A())`)
* `cats.effect.IO` (e.g. `cats.effect.IO.pure(new A())`)

Using the dependencies from the given arguments it creates an instance of the given type. Any missing instances are created using their primary constructor, provided that the dependencies are met. If this is not possible, a compile-time error is reported. In other words, a `wireRec` is performed, bypassing the instances search phase. 

The result of the wiring is always wrapped in `cats.effect.Resource`. For example:

```scala
import cats.effect._

class DatabaseAccess()

class SecurityFilter private (databaseAccess: DatabaseAccess)
object SecurityFilter {
  def apply(databaseAccess: DatabaseAccess): SecurityFilter = new SecurityFilter(databaseAccess)
}

class UserFinder(databaseAccess: DatabaseAccess, securityFilter: SecurityFilter)
class UserStatusReader(databaseAccess: DatabaseAccess, userFinder: UserFinder)

object UserModule {
  import com.softwaremill.macwire.autocats._

  val theDatabaseAccess: Resource[IO, DatabaseAccess] = Resource.pure(new DatabaseAccess())

  val theUserStatusReader: Resource[IO, UserStatusReader] = autowire[UserStatusReader](theDatabaseAccess)
}
```

will generate:

```scala
[...]
object UserModule {
  import com.softwaremill.macwire.autocats._

  val theDatabaseAccess: Resource[IO, DatabaseAccess] = Resource.pure(new DatabaseAccess())

  val theUserStatusReader: Resource[IO, UserStatusReader] = UserModule.this.theDatabaseAccess.flatMap(
    da => Resource.pure[IO, UserStatusReader](new UserStatusReader(da, new UserFinder(da, SecurityFilter.apply(da))))
  )
}
```

# Interceptors

Dependency:

```
"com.softwaremill.macwire" %% "proxy" % "2.6.0"
```

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

# Qualifiers

> [!NOTE]
> While the below works both in Scala 2 & Scala 3, in Scala 3, you can use the built-in
> [opaque types](https://docs.scala-lang.org/scala3/reference/other-new-features/opaques.html)
> instead.

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

```scala
class Berry()
trait Black
trait Blue

case class Basket(blueberry: Berry @@ Blue, blackberry: Berry @@ Black)

lazy val blueberry = wire[Berry].taggedWith[Blue]
lazy val blackberry = wire[Berry].taggedWith[Black]
lazy val basket = wire[Basket]
```

Multiple tags can be combined using the `andTaggedWith` method. E.g. if we had a berry that is both blue and black:

```scala
lazy val blackblueberry = wire[Berry].taggedWith[Black].andTaggedWith[Blue]
```

The resulting value has type `Berry @ (Black with Blue)` and can be used both as a blackberry and as a blueberry.

# Development

## Debugging

To print debugging information on what MacWire does when looking for values, and what code is generated, set the
`macwire.debug` system property. E.g. with SBT, start using `sbt -Dmacwire.debug`.

## Future development - vote!

Take a look at the [available issues](https://github.com/adamw/macwire/issues). If you'd like to see one developed
please vote on it. Or maybe you'll attempt to create a pull request?

# Platform and version-specifics

## Scala.js

Macwire also works with [Scala.js](http://www.scala-js.org/). For an example, see here:
[Macwire+Scala.js example](https://github.com/adamw/macwire/tree/master/examples/scalajs).

## Scala3 support

The Scala 3 version is written to be compatible with Scala 2 where possible. Currently there are a few missing features:

* [wire from parent scope](https://github.com/lampepfl/dotty/issues/13105)
* [wire from imports](https://github.com/lampepfl/dotty/issues/12965)
* [wire in pattern matching](https://github.com/softwaremill/macwire/issues/170)
* [`wiredInModule`](https://github.com/softwaremill/macwire/issues/171) 
* [`@Module`](https://github.com/softwaremill/macwire/issues/172)

For full list of incompatibilities take a look at `tests/src/test/resources/test-cases` and `util-tests/src/test/resources/test-cases` .

# Other

## Commercial Support

We offer commercial support for MacWire and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2013-2024 SoftwareMill [https://softwaremill.com](https://softwaremill.com).

