import sbt._
import sbt.Keys._

object BuildSettings {

  val commonSettings = Defaults.coreDefaultSettings ++ Seq (
    organization  := "com.softwaremill.macwire",
    version       := "2.1.0",
    scalaVersion  := "2.11.7",
    // Sonatype OSS deployment
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
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
      </developers>,
    licenses      := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
    homepage      := Some(new java.net.URL("http://www.softwaremill.com"))
  )

  val testSettings = commonSettings ++ Seq(
    publishArtifact := false,
    scalacOptions ++= Seq("-Ywarn-dead-code"),
    // Otherwise when running tests in sbt, the macro is not visible
    // (both macro and usages are compiled in the same compiler run)
    fork in Test := true
  )
}

object Dependencies {
  val scalatest     = "org.scalatest" %% "scalatest"  % "2.2.5"
  val javassist     = "org.javassist"  % "javassist"  % "3.20.0-GA"
}

object MacwireBuild extends Build {
  import BuildSettings._
  import Dependencies._

  lazy val root = project.in(file(".")).
    settings(commonSettings).
    settings(
      publishArtifact := false).
    aggregate(
      util, macros, proxy, tests, tests2, testUtil, utilTests, examplesScalatra)

  lazy val util = project.in(file("util")).
    settings(commonSettings)

  lazy val macros = project.in(file("macros")).
    settings(commonSettings).
    settings(
      libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value).
    dependsOn(util % "provided")

  lazy val proxy = project.in(file("proxy")).
    settings(commonSettings).
    settings(
      libraryDependencies ++= Seq(javassist, scalatest)).
    dependsOn(macros % "test")

  lazy val testUtil = project.in(file("test-util")).
    settings(testSettings).
    settings(
      libraryDependencies ++= Seq(
        scalatest,
        "org.scala-lang" % "scala-compiler" % scalaVersion.value))

  lazy val tests = project.in(file("tests")).
    settings(testSettings).
    dependsOn(
      macros % "provided",
      testUtil % "test",
      proxy)

  lazy val utilTests = project.in(file("util-tests")).
    settings(testSettings).
    dependsOn(
      macros % "provided",
      util % "test",
      testUtil % "test")

  // The tests here are that the tests compile.
  lazy val tests2 = project.in(file("tests2")).
    settings(testSettings).
    settings(
      libraryDependencies += scalatest).
    dependsOn(
      util, macros % "provided", proxy)

  lazy val examplesScalatra: Project = {
    val ScalatraVersion = "2.3.1"
    val scalatraCore = "org.scalatra" %% "scalatra" % ScalatraVersion
    val scalatraScalate = "org.scalatra" %% "scalatra-scalate" % ScalatraVersion
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
    val jetty = "org.eclipse.jetty" % "jetty-webapp" % "9.3.3.v20150827" % "compile"
    val servletApi = "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "compile" artifacts (Artifact("javax.servlet", "jar", "jar"))

    Project(
      "examples-scalatra",
      file("examples/scalatra"),
      settings = commonSettings ++ Seq(
        publishArtifact := false,
        classpathTypes ~= (_ + "orbit"),
        libraryDependencies ++= Seq(scalatraCore, scalatraScalate, jetty, servletApi, logback)
      )
    ) dependsOn(util, macros % "provided", proxy)
  }

  // Enabling debug project-wide. Can't find a better way to pass options to scalac.
  System.setProperty("macwire.debug", "")
}
