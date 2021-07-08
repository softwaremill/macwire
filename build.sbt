import com.softwaremill.UpdateVersionInDocs

import sbt._
import sbt.Keys._

val scala2 = Seq("2.12.11", "2.13.2")
val scala3 = "3.0.1-RC1"
val scala2And3Versions = scala2 :+ scala3

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

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.macwire",
  version := "2.3.8",
  // crossScalaVersions := scala2 :+ scala3,
  // Sonatype OSS deployment
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
  pomExtra :=
    <scm>
      <url>git@github.com:adamw/macwire.git</url>
      <connection>scm:git:git@github.com:adamw/macwire.git</connection>
    </scm>
      <developers>
        <developer>
          <id>adamw</id>
          <name>Adam Warski</name>
          <url>http://www.warski.org</url>
        </developer>
        <developer>
          <id>backuitist</id>
          <name>Bruno Bieth</name>
          <url>https://github.com/backuitist</url>
        </developer>
        <developer>
          <id>mkubala</id>
          <name>Marcin Kubala</name>
          <url>https://github.com/mkubala</url>
        </developer>
        <developer>
          <id>pawel.panasewicz</id>
          <name>Paweł Panasewicz</name>
          <url>http://panasoft.pl</url>
        </developer>
      </developers>,
  licenses := (
    "Apache2",
    new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")
  ) :: Nil,
  homepage := Some(new java.net.URL("http://www.softwaremill.com"))
)

val testSettings = commonSettings ++ Seq(
  publishArtifact := false,
  scalacOptions ++= Seq("-Ywarn-dead-code"),
  // Otherwise when running tests in sbt, the macro is not visible
  // (both macro and usages are compiled in the same compiler run)
  fork in Test := true
)

val tagging = "com.softwaremill.common" %% "tagging" % "2.3.1"
val scalatest = "org.scalatest" %% "scalatest" % "3.2.9"
val javassist = "org.javassist" % "javassist" % "3.20.0-GA"
val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.5.23"
val javaxInject = "javax.inject" % "javax.inject" % "1"

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(name := "macwire", publishArtifact := false)
  .aggregate(
    List(
    util,
    macros,
    proxy,
    tests,
    tests2,
    testUtil,
    utilTests,
    macrosAkka,
    macrosAkkaTests
    ).flatMap(_.projectRefs): _*
  )

lazy val util = projectMatrix
  .in(file("util"))
  .settings(libraryDependencies += tagging)
  .settings(commonSettings)
  .jvmPlatform(
    scalaVersions = scala2And3Versions
  )

lazy val macros = projectMatrix
  .in(file("macros"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= reflectLibrary(scalaVersion.value),
    versionSpecificScalaSources
  )
  .dependsOn(util % "provided")
  .jvmPlatform(
    scalaVersions = scala2And3Versions
  )

lazy val proxy = projectMatrix
  .in(file("proxy"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(javassist, scalatest % "test"))
  .dependsOn(macros % "test")
  .jvmPlatform(
    scalaVersions = scala2And3Versions
  )

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

lazy val tests = projectMatrix
  .in(file("tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", testUtil % "test", proxy)
  .jvmPlatform(
    scalaVersions = scala2And3Versions
  )

lazy val utilTests = projectMatrix
  .in(file("util-tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", util % "test", testUtil % "test")
  .jvmPlatform(
    scalaVersions = scala2And3Versions
  )

// The tests here are that the tests compile.
lazy val tests2 = projectMatrix
  .in(file("tests2"))
  .settings(testSettings)
  .settings(
    libraryDependencies += scalatest % "test"
  )
  .dependsOn(util, macros % "provided", proxy)
  .jvmPlatform(
    // scalaVersions = scala2And3Versions
    scalaVersions = List(scala3)
  )

lazy val macrosAkka = projectMatrix
  .in(file("macrosAkka"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(akkaActor % "provided"))
  .dependsOn(macros)
  .jvmPlatform(
    scalaVersions = scala2
  )

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
  .jvmPlatform(
    scalaVersions = scala2
  )

compile in Compile := {
  // Enabling debug project-wide. Can't find a better way to pass options to scalac.
  System.setProperty("macwire.debug", "")

  (compile in Compile).value
}
