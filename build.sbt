Era7.scalaProject

sbtPlugin := true

name := "sbt-statika"

description := "Default sbt project settings for statika bundles"

organization := "ohnosequences"

bucketSuffix := "era7.com"


// plugins which will be inherrited by anybody who uses this plugin:
addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.7.0")

resolvers += "Era7 maven snapshots"  at "http://snapshots.era7.com.s3.amazonaws.com"

addSbtPlugin("ohnosequences" % "era7-sbt-settings" % "0.2.0-SNAPSHOT")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")
