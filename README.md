MacWire
=======

Notes
-----

When referencing wired values within the trait, e.g.:

   case class A()
   case class B(a: A)

   lazy val theA = wire[A]
    // reference to theA; if for some reason we need explicitly write the constructor call
   lazy val theB = new B(thaA)

to avoid recursive type compiler errors, the referenced wired value needs a type ascription, e.g.:

   lazy val theA: A = wire[A]

Debugging
---------

The print debugging information on what MacWire does when looking for values, and what code is generated, set the
`macwire.debug` system property. E.g. with SBT, just add a `System.setProperty("macwire.debug", "")` line to your
build file.