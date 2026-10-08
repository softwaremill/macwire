import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.{updateDocs, ossPublishSettings}
import com.softwaremill.UpdateVersionInDocs

import sbt._
import sbt.Keys._

Global / excludeLintKeys ++= Set(ideSkipProject)

val scala2_12 = "2.12.21"
val scala2_13 = "2.13.18"

val scala2 = List(scala2_12, scala2_13)
val scala3 = "3.9.0"

val scala2And3Versions = scala2 :+ scala3

val ideScalaVersion = scala3

def compilerLibrary(scalaVersion: String) = {
  if (scalaVersion == scala3) {
    Seq("org.scala-lang" %% "scala3-compiler" % scalaVersion)
  } else {
    Seq("org.scala-lang" % "scala-compiler" % scalaVersion)
  }
}

def reflectLibrary(scalaVersion: String) = {
  if (scalaVersion == scala3) {
    Seq.empty
  } else {
    Seq("org.scala-lang" % "scala-reflect" % scalaVersion)
  }
}

val versionSpecificScalaSources = {
  Compile / unmanagedSourceDirectories := {
    val current = (Compile / unmanagedSourceDirectories).value
    val sv = (Compile / scalaVersion).value
    val baseDirectory = (Compile / scalaSource).value
    val suffixes = CrossVersion.partialVersion(sv) match {
      case Some((2, 13)) => List("2", "2.13+")
      case Some((2, _))  => List("2", "2.13-")
      case Some((3, _))  => List("3")
      case _             => Nil
    }
    val versionSpecificSources = suffixes.map(s => new File(baseDirectory.getAbsolutePath + "-" + s))
    versionSpecificSources ++ current
  }
}

commonSmlBuildSettings
ossPublishSettings

organization := "com.softwaremill.macwire"
ideSkipProject := (scalaVersion.value != ideScalaVersion) || thisProjectRef.value.project.contains("JS")
bspEnabled := !ideSkipProject.value
scalacOptions ~= (_.filterNot(Set("-Wconf:cat=other-match-analysis:error"))) // doesn't play well with macros

val scala2Source3 = scalacOptions ++= {
  if (scalaVersion.value == scala3) Nil else Seq("-Xsource:3")
}

val testSettings = Seq(
  publishArtifact := false,
  scalacOptions ++= Seq("-Ywarn-dead-code"),
  // Otherwise when running tests in sbt, the macro is not visible
  // (both macro and usages are compiled in the same compiler run)
  Test / fork := true,
  exportJars := false,
  // in sbt 2 forked tests are run by a worker, so java.class.path doesn't contain the test classpath needed by the Scala 3 compile tests
  Test / javaOptions += {
    val converter = fileConverter.value
    val classpath = (Test / fullClasspath).value.map(a => converter.toPath(a.data).toString)
    s"-Dmacwire.test.classpath=${classpath.mkString(java.io.File.pathSeparator)}"
  }
)

val tagging = ("com.softwaremill.common" %% "tagging" % "2.3.5").platform(Platform.jvm)
val scalatest = "org.scalatest" %% "scalatest" % "3.2.20"
val javassist = "org.javassist" % "javassist" % "3.33.0-GA"
val akkaActor = ("com.typesafe.akka" %% "akka-actor" % "2.6.21").platform(Platform.jvm)
val pekkoActor = ("org.apache.pekko" %% "pekko-actor" % "1.7.0").platform(Platform.jvm)
val javaxInject = "javax.inject" % "javax.inject" % "1"
val cats = ("org.typelevel" %% "cats-core" % "2.13.0").platform(Platform.jvm)
val catsEffect = ("org.typelevel" %% "cats-effect" % "3.7.1").platform(Platform.jvm)

lazy val root = rootProject
  .settings(name := "macwire", publishArtifact := false)
  .autoAggregate

lazy val util = projectMatrix
  .in(file("util"))
  .settings(libraryDependencies += tagging, scala2Source3)
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(scalaVersions = scala2And3Versions)
  .nativePlatform(scalaVersions = scala2And3Versions)

lazy val macros = projectMatrix
  .in(file("macros"))
  .settings(
    libraryDependencies ++= reflectLibrary(scalaVersion.value),
    versionSpecificScalaSources
  )
  .dependsOn(util % "provided")
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(scalaVersions = scala2And3Versions)
  .nativePlatform(scalaVersions = scala2And3Versions)

lazy val proxy = projectMatrix
  .in(file("proxy"))
  .settings(
    libraryDependencies ++= Seq(javassist, scalatest % Test),
    compileOrder := CompileOrder.JavaThenScala,
    scala2Source3,
    javaOptions += "--add-opens java.base/java.lang=ALL-UNNAMED"
  )
  .dependsOn(macros % Test)
  .jvmPlatform(scalaVersions = scala2And3Versions)

lazy val testUtil = projectMatrix
  .in(file("test-util"))
  .settings(testSettings)
  .settings(
    libraryDependencies ++= Seq(
      scalatest,
      javaxInject
    ) ++ compilerLibrary(scalaVersion.value)
  )
  .jvmPlatform(
    scalaVersions = scala2And3Versions
  )
  .jvmPlatform(scalaVersions = scala2)

lazy val tests = projectMatrix
  .in(file("tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", testUtil % Test, proxy)
  .jvmPlatform(scalaVersions = scala2And3Versions)

lazy val utilTests = projectMatrix
  .in(file("util-tests"))
  .settings(testSettings, scala2Source3)
  .dependsOn(macros % "provided", util % Test, testUtil % Test)
  .jvmPlatform(scalaVersions = scala2And3Versions)

// The tests here are that the tests compile.
lazy val tests2 = projectMatrix
  .in(file("tests2"))
  .settings(testSettings)
  .settings(libraryDependencies += scalatest % Test)
  .dependsOn(util, macros % "provided", proxy)
  .jvmPlatform(scalaVersions = scala2And3Versions)

lazy val macrosAkka = projectMatrix
  .in(file("macrosAkka"))
  .settings(libraryDependencies ++= Seq(akkaActor % "provided"))
  .dependsOn(macros)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val macrosPekko = projectMatrix
  .in(file("macrosPekko"))
  .settings(libraryDependencies ++= Seq(pekkoActor % "provided"))
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

lazy val macrosPekkoTests = projectMatrix
  .in(file("macrosPekkoTests"))
  .settings(
    // Needed to avoid cryptic EOFException crashes in forked tests in Travis
    // example failure: https://travis-ci.org/adamw/macwire/builds/191382122
    // see: https://github.com/travis-ci/travis-ci/issues/3775
    javaOptions += "-Xmx1G"
  )
  .settings(testSettings)
  .settings(libraryDependencies ++= Seq(scalatest, tagging, pekkoActor))
  .dependsOn(macrosPekko, testUtil)
  .jvmPlatform(scalaVersions = scala2)

lazy val macrosAutoCats = projectMatrix
  .in(file("macrosAutoCats"))
  .settings(libraryDependencies ++= Seq(catsEffect, cats))
  .dependsOn(macros)
  .jvmPlatform(scalaVersions = scala2)
  .jsPlatform(scalaVersions = scala2)

lazy val macrosAutoCatsTests = projectMatrix
  .in(file("macrosAutoCatsTests"))
  .settings(testSettings)
  .settings(libraryDependencies ++= Seq(scalatest, catsEffect, tagging))
  .dependsOn(macrosAutoCats, testUtil)
  .jvmPlatform(scalaVersions = scala2)
