import ohnosequences.sbt._

Era7.allSettings


sbtPlugin := true

name := "sbt-statika"

description := "Default sbt project settings for statika bundles"

homepage := Some(url("https://github.com/ohnosequences/sbt-statika"))

organization := "ohnosequences"

organizationHomepage := Some(url("http://ohnosequences.com"))

licenses := Seq("AGPLv3" -> url("http://www.gnu.org/licenses/agpl-3.0.txt"))

scalaVersion := "2.10.3"

scalacOptions ++= Seq(
  "-feature"
, "-deprecation"
, "-language:postfixOps"
)

bucketSuffix := "era7.com"

// plugins which will be inherrited by anybody who uses this plugin:
addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.7.0")

addSbtPlugin("ohnosequences" % "era7-sbt-release" % "0.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.0")
