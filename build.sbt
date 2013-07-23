import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

sbtPlugin := true

name := "sbt-statika"

organization := "ohnosequences"

version := "0.4.0-SNAPSHOT"

description := "Default sbt project settings for statika bundles"

scalaVersion := "2.9.2"

// crossScalaVersions := Seq("2.9.1", "2.9.2", "2.10.0")

publishMavenStyle := false


s3pattern in Global := "[organisation]/[module]/[revision]/[type]s/[artifact].[ext]"

publishTo <<= (isSnapshot, s3resolver) { 
                (snapshot,   resolver) => 
  val prefix = if (snapshot) "snapshots" else "releases"
  resolver("Era7 "+prefix+" S3 bucket", "s3://"+prefix+".era7.com")
}

resolvers ++= Seq ( 
  Resolver.typesafeRepo("releases")
, Resolver.sonatypeRepo("releases")
, Resolver.sonatypeRepo("snapshots")
, Resolver.url("Era7 Ivy Snapshots", url("http://snapshots.era7.com.s3.amazonaws.com"))(
    Patterns("[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"))
, Resolver.url("Era7 Ivy Releases", url("http://releases.era7.com.s3.amazonaws.com"))(
    Patterns("[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"))
, "Era7 Releases"  at "http://releases.era7.com.s3.amazonaws.com"
, "Era7 Snapshots"   at "http://snapshots.era7.com.s3.amazonaws.com"   
)

s3credentialsFile in Global := Some("/home/evdokim/era7.prop")


addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.8.0")

addSbtPlugin("ohnosequences" % "sbt-buildinfo" % "0.3.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.6")

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.4.0")

// sbt-release settings

releaseSettings

releaseProcess <<= thisProjectRef apply { ref =>
  Seq[ReleaseStep](
    checkSnapshotDependencies
  , inquireVersions
  , setReleaseVersion
  , publishArtifacts
  , setNextVersion
  )
}