import sbt._
import sbt.Keys._

excludeLintKeys in Global ++= Set(ideSkipProject)

val scala2_11 = "2.11.12"
val scala2_12 = "2.12.13"
val scala2_13 = "2.13.6"

val scala2 = List(scala2_11, scala2_12, scala2_13)

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.macwire",
  ideSkipProject := (scalaVersion.value == scala2_12) || thisProjectRef.value.project.contains("JS"),
  scalacOptions ~= (_.filterNot(Set("-Wconf:cat=other-match-analysis:error"))) // doesn't play well with macros
)

val testSettings = commonSettings ++ Seq(
  publishArtifact := false,
  scalacOptions ++= Seq("-Ywarn-dead-code"),
  // Otherwise when running tests in sbt, the macro is not visible
  // (both macro and usages are compiled in the same compiler run)
  Test / fork := true
)

val tagging = "com.softwaremill.common" %% "tagging" % "2.2.1"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.8"
val javassist = "org.javassist" % "javassist" % "3.20.0-GA"
val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.5.23"
val javaxInject = "javax.inject" % "javax.inject" % "1"
def scalaCompiler(v: String) = "org.scala-lang" % "scala-compiler" % v

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(name := "macwire", publishArtifact := false)
  .aggregate(
    util.projectRefs ++
      macros.projectRefs ++
      proxy.projectRefs ++
      tests.projectRefs ++
      tests2.projectRefs ++
      testUtil.projectRefs ++
      utilTests.projectRefs ++
      macrosAkka.projectRefs ++
      macrosAkkaTests.projectRefs: _*
  )

lazy val util = projectMatrix
  .in(file("util"))
  .settings(libraryDependencies += tagging)
  .settings(commonSettings)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val macros = projectMatrix
  .in(file("macros"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
  .dependsOn(util % "provided")
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val proxy = projectMatrix
  .in(file("proxy"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(javassist, scalatest % "test"))
  .dependsOn(macros % "test")
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val testUtil = projectMatrix
  .in(file("test-util"))
  .settings(testSettings)
  .settings(
    libraryDependencies ++= Seq(
      scalatest,
      scalaCompiler(scalaVersion.value),
      javaxInject
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val tests = projectMatrix
  .in(file("tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", testUtil % "test", proxy)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val utilTests = projectMatrix
  .in(file("util-tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", util % "test", testUtil % "test")
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

// The tests here are that the tests compile.
lazy val tests2 = projectMatrix
  .in(file("tests2"))
  .settings(testSettings)
  .settings(libraryDependencies += scalatest % "test")
  .dependsOn(util, macros % "provided", proxy)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val macrosAkka = projectMatrix
  .in(file("macrosAkka"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(akkaActor % "provided"))
  .dependsOn(macros)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val macrosAkkaTests = projectMatrix
  .in(file("macrosAkkaTests"))
  .settings(
    // Needed to avoid cryptic EOFException crashes in forked tests in Travis
    // example failure: https://travis-ci.org/adamw/macwire/builds/191382122
    // see: https://github.com/travis-ci/travis-ci/issues/3775
    javaOptions += "-Xmx1G"
  )
  .settings(testSettings)
  .settings(libraryDependencies ++= Seq(scalatest, tagging, akkaActor))
  .dependsOn(macrosAkka, testUtil)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

Compile / compile := {
  // Enabling debug project-wide. Can't find a better way to pass options to scalac.
  System.setProperty("macwire.debug", "")

  (Compile / compile).value
}
