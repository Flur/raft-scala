name := "raft-scala"

version := "1.0"

scalaVersion := "2.13.6"

enablePlugins(AkkaGrpcPlugin, JavaAppPackaging, DockerComposePlugin)

dockerImageCreationTask := (publishLocal in Docker).value

val AkkaVersion = "2.6.9"
val AkkaHttpVersion = "10.2.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  // logging
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // tests
//  "org.scalactic" %% "scalactic" % "3.2.9",
//  "org.scalatest" %% "scalatest" % "3.2.9" % "test",

  "org.scalatest" %% "scalatest" % "3.1.4" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
)
