import sbtrelease._
import sbt._
import Defaults._

releaseSettings


sbtPlugin := true

name := "sbt-statika"

organization := "ohnosequences"

description := "Default sbt project settings for statika bundles"


scalaVersion := "2.10.3"

scalacOptions ++= Seq(
  "-feature"
, "-deprecation"
, "-language:postfixOps"
)


publishMavenStyle := true

publishTo <<= (isSnapshot, s3credentials) { 
                (snapshot,   credentials) => 
  val prefix = if (snapshot) "snapshots" else "releases"
  credentials map S3Resolver(
      "Era7 "+prefix+" S3 publishing bucket"
    , "s3://"+prefix+".era7.com"
    ).toSbtResolver
}

resolvers ++= Seq ( 
  "Era7 maven releases"  at "http://releases.era7.com.s3.amazonaws.com"
// , "Era7 maven snapshots" at "http://snapshots.era7.com.s3.amazonaws.com"   
)

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.6.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")
