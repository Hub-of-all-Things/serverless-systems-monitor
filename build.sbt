import sbt.Keys._
import sbt._

name := "org/hatdex/serverless/aws/todos"

resolvers += Resolver.sonatypeRepo("public")
scalaVersion := "2.12.4"
assemblyJarName in assembly := "todos.jar"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-events" % "1.3.0",
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "com.github.seratch" %% "awscala" % "0.5.+",
  "com.typesafe.play" %% "play-json" % "2.6.8",
  "org.slf4j" % "slf4j-api" % "1.7.24"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
//  "-Xlog-implicits",
  "-Xfatal-warnings")
