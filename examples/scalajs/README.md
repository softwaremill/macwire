Scala.js+Macwire example
===

This is an example project showing that [Macwire](https://github.com/adamw/macwire) works with 
[Scala.js](http://www.scala-js.org/)!

To see it yourself, follow these steps:

1. you need to have SBT installed
2. take a look at the sources. There's a very simple two-class object graph, being wired by Macwire in the
`MainModule` trait
3. generate the javascript by running `fastOptJS`
4. now open `index.html` in a browser and enjoy the results! When clicking the button, a method on the wired
object graph is invoked, and the object graph itself is printed as well.