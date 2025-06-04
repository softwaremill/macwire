addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")

val sbtSoftwareMillVersion = "2.0.26"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)

addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.19.0")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.7")
