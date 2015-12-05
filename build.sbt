name := """ImplicitLogging"""

version := "0.1"

scalaVersion := "2.11.7"

libraryDependencies ++= {
  Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.3" % "runtime",
    "org.slf4j" % "slf4j-api" % "1.7.9",
    "org.slf4j" % "slf4j-simple" % "1.7.9",
    "joda-time" % "joda-time" % "2.9.1",
    "io.spray" %% "spray-json" % "1.3.0",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "org.easymock" % "easymock" % "3.2" % "test"
  )
}
