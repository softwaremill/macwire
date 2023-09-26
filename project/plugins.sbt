addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.1")

val sbtSoftwareMillVersion = "2.0.18"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)

addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.1.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.14.0")
