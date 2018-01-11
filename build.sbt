import sbt.Keys._
import sbt._

name := "org/hatdex/serverless/aws/todos"

resolvers += Resolver.sonatypeRepo("public")
scalaVersion := "2.12.4"
assemblyJarName in assembly := "todos.jar"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-events" % "1.3.0",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.github.seratch" %% "awscala" % "0.6.+",
  "io.symphonia" % "lambda-logging" % "1.0.0",
  "com.typesafe.play" %% "play-json" % "2.6.8",
  "org.specs2" %% "specs2-core" % "4.0.0" % "provided",
  "org.specs2" %% "specs2-matcher-extra" % "4.0.0" % "provided",
  "org.specs2" %% "specs2-mock" % "4.0.0" % "provided"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings")

test in assembly := {}
