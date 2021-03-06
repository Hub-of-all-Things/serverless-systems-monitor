import sbt.Keys._
import sbt._

name := "status-monitor"

resolvers += Resolver.sonatypeRepo("public")
resolvers += "HAT Library Artifacts Releases" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-releases.hubofallthings.com"
resolvers += "HAT Library Artifacts Snapshots" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-snapshots.hubofallthings.com"

scalaVersion := "2.12.4"
assemblyJarName in assembly := "status-monitor.jar"

libraryDependencies ++= Seq(
  "org.hatdex" %% "aws-lambda-scala-handler" % "0.0.2-SNAPSHOT",
  "com.github.seratch" %% "awscala" % "0.6.+",
  "org.specs2" %% "specs2-core" % "4.0.0" % "provided",
  "org.specs2" %% "specs2-matcher-extra" % "4.0.0" % "provided",
  "org.specs2" %% "specs2-mock" % "4.0.0" % "provided",
  "com.typesafe.akka" %% "akka-actor" % "2.5.9",
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.1.3",
  "com.typesafe.play" %% "play-ws-standalone-json" % "1.1.3",
  "com.amazonaws" % "aws-java-sdk-sns" % "1.11.264"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings")

test in assembly := {}
