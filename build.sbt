name := "power-school"

version := "0.1"

scalaVersion := "2.13.10"


libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.2.8",
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.2.8",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.2.8",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui" % "1.2.8"
)

libraryDependencies += "org.http4s" %% "http4s-blaze-server" % "1.0.0-M21"

