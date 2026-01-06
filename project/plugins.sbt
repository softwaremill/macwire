addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")

val sbtSoftwareMillVersion = "2.1.1"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)

addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.3")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.9")
