// addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")

// addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.3")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.7.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.1")

val sbtSoftwareMillVersion = "2.0.3"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)