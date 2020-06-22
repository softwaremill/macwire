import sbt._
import sbt.Keys._

val commonSettings = Defaults.coreDefaultSettings ++ Seq(
  organization := "com.softwaremill.macwire",
  version := "2.3.7",
  scalaVersion := "2.12.11",
  crossScalaVersions := Seq("2.11.12", scalaVersion.value, "2.13.2"),
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
    util,
    macros,
    proxy,
    tests,
    tests2,
    testUtil,
    utilTests,
    macrosAkka,
    macrosAkkaTests
  )

lazy val util = project
  .in(file("util"))
  .settings(libraryDependencies += tagging)
  .settings(commonSettings)

lazy val macros = project
  .in(file("macros"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
  .dependsOn(util % "provided")

lazy val proxy = project
  .in(file("proxy"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(javassist, scalatest % "test"))
  .dependsOn(macros % "test")

lazy val testUtil = project
  .in(file("test-util"))
  .settings(testSettings)
  .settings(
    libraryDependencies ++= Seq(
      scalatest,
      scalaCompiler(scalaVersion.value),
      javaxInject
    )
  )

lazy val tests = project
  .in(file("tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", testUtil % "test", proxy)

lazy val utilTests = project
  .in(file("util-tests"))
  .settings(testSettings)
  .dependsOn(macros % "provided", util % "test", testUtil % "test")

// The tests here are that the tests compile.
lazy val tests2 = project
  .in(file("tests2"))
  .settings(testSettings)
  .settings(libraryDependencies += scalatest % "test")
  .dependsOn(util, macros % "provided", proxy)

lazy val macrosAkka = project
  .in(file("macrosAkka"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(akkaActor % "provided"))
  .dependsOn(macros)

lazy val macrosAkkaTests = project
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

compile in Compile := {
  // Enabling debug project-wide. Can't find a better way to pass options to scalac.
  System.setProperty("macwire.debug", "")

  (compile in Compile).value
}
