import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

sbtPlugin := true

name := "sbt-statika"

organization := "ohnosequences"

description := "Default sbt project settings for statika bundles"

scalaVersion := "2.9.2"

publishTo <<= (isSnapshot, s3credentials) { 
                (snapshot,   credentials) => 
  val prefix = if (snapshot) "snapshots" else "releases"
  credentials map s3resolver("Era7 "+prefix+" S3 bucket", "s3://"+prefix+".era7.com")
}

resolvers ++= Seq ( 
  Resolver.typesafeRepo("releases")
, Resolver.sonatypeRepo("releases")
, Resolver.sonatypeRepo("snapshots")
, "Era7 Releases"  at "http://releases.era7.com.s3.amazonaws.com"
, "Era7 Snapshots"   at "http://snapshots.era7.com.s3.amazonaws.com"   
)

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.8.0")

addSbtPlugin("ohnosequences" % "sbt-buildinfo" % "0.3.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.7")

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.4.0")

// sbt-release settings

releaseSettings
