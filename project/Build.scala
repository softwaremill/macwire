import sbt._
import Keys._

object BuildSettings {
  val buildSettings = Defaults.coreDefaultSettings ++ Seq (
    organization  := "com.softwaremill.macwire",
    version       := "1.0.3",
    scalaVersion  := "2.11.6",
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
}

object Dependencies {
  val scalatest     = "org.scalatest" %% "scalatest"  % "2.2.5"       % "test"
  val javassist     = "org.javassist"  % "javassist"  % "3.19.0-GA"
}

object MacwireBuild extends Build {
  import BuildSettings._
  import Dependencies._

  lazy val root: Project = Project(
    "root",
    file("."),
    settings = buildSettings ++ Seq(publishArtifact := false)
  ) aggregate(macros, runtime, tests, tests2, examplesScalatra)

  lazy val macros: Project = Project(
    "macros",
    file("macros"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _))
  )

  lazy val runtime: Project = Project(
    "runtime",
    file("runtime"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(javassist, scalatest))
  ) dependsOn(macros % "test")

  lazy val tests: Project = Project(
    "tests",
    file("tests"),
    settings = buildSettings ++ Seq(
      publishArtifact := false,
      libraryDependencies ++= Seq(scalatest),
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "test"),
      scalacOptions ++= Seq("-Ywarn-dead-code"),
      // Otherwise when running tests in sbt, the macro is not visible
      // (both macro and usages are compiled in the same compiler run)
      fork in Test := true)
  ) dependsOn(macros, runtime)

  // The tests here are that the tests compile.
  lazy val tests2: Project = Project(
    "tests2",
    file("tests2"),
    settings = buildSettings ++ Seq(
      publishArtifact := false,
      libraryDependencies ++= Seq(scalatest),
      // Otherwise when running tests in sbt, the macro is not visible
      // (both macro and usages are compiled in the same compiler run)
      fork in test := true)
  ) dependsOn(macros, runtime)

  lazy val examplesScalatra: Project = {
    val ScalatraVersion = "2.3.1"
    val scalatraCore = "org.scalatra" %% "scalatra" % ScalatraVersion
    val scalatraScalate = "org.scalatra" %% "scalatra-scalate" % ScalatraVersion
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
    val jetty = "org.eclipse.jetty" % "jetty-webapp" % "9.2.0.v20140526" % "compile"
    val servletApi = "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "compile" artifacts (Artifact("javax.servlet", "jar", "jar"))

    Project(
      "examples-scalatra",
      file("examples/scalatra"),
      settings = buildSettings ++ Seq(
        publishArtifact := false,
        classpathTypes ~= (_ + "orbit"),
        libraryDependencies ++= Seq(scalatraCore, scalatraScalate, jetty, servletApi, logback)
      )
    ) dependsOn(macros, runtime)
  }

  // Enabling debug project-wide. Can't find a better way to pass options to scalac.
  System.setProperty("macwire.debug", "")
}
