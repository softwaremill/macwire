package foo.bar

import com.softwaremill.macwire.Macwire

object FindByVarName extends Macwire {
  class Foo
  class Bar(foo: Foo)
  class Baz(baz: Foo)

  lazy val foo = wire[Foo]
  lazy val baz = wire[Foo]

  lazy val bar = wire[Bar]
  lazy val bar2 = wire[Baz]
}
