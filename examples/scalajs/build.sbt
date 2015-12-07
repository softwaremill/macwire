enablePlugins(ScalaJSPlugin)

name := "Scala.js+Macwire example"

scalaVersion := "2.11.7"

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.2.0"

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.8.2"