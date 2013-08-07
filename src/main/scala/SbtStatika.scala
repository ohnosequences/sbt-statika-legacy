import sbt._
import Keys._

import com.typesafe.sbt.SbtStartScript._

import sbtbuildinfo.Plugin._

import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

import SbtS3Resolver._

object SbtStatika extends Plugin {

  lazy val bundlePackage = SettingKey[String]("bundle-package",
    "Package name for the bundle")

  lazy val bundleObject = SettingKey[String]("bundle-object",
    "Supposed name of the bundle object")

  lazy val isPrivate = SettingKey[Boolean]("is-private", 
    "If true, publish to private S3 bucket, else to public")

  lazy val statikaVersion = SettingKey[String]("statika-version",
    "statika library version")

  lazy val statikaResolvers = SettingKey[Seq[S3Resolver]]("statika-resolvers",
    "Resolvers for statika dependencies")

  lazy val statikaPrivateResolvers = SettingKey[Seq[S3Resolver]]("statika-private-resolvers",
    "Private resolvers for statika dependencies")

  case class S3Bucket(url: String) {
    override def toString = url
    def toHttp = if(url.startsWith("s3://"))
         "http://"+ url.stripPrefix("s3://") +".s3.amazonaws.com"
      else url
  }
  // short alias
  def s3(url: String): S3Bucket = S3Bucket(url)

  case class S3Resolver(name: String, bucket: S3Bucket) {
    override def toString = """\"%s\" at \"%s\" """ format (name, bucket)
  }

  // convertion from string for nice syntax
  class StringAtS3(name: String) {
    def at(bucket: S3Bucket) = S3Resolver(name, bucket)
  }
  implicit def StringAtS3(s: String) = new StringAtS3(s)
  
  // convertion to Resolver
  implicit def s3ToMvn(s3: S3Resolver): Resolver = s3.name at s3.bucket.toHttp


  override def settings = 
    startScriptForClassesSettings ++
    releaseSettings ++
    buildInfoSettings ++ 
    Seq(
    // resolvers

      resolvers ++= Seq ( 
        Resolver.typesafeRepo("releases")
      , Resolver.sonatypeRepo("releases")
      , Resolver.sonatypeRepo("snapshots")
      )

    , statikaResolvers := Seq(
        "Era7 Releases"  at s3("s3://releases.era7.com")
      , "Era7 Snapshots" at s3("s3://snapshots.era7.com")
      , "Statika public releases" at s3("s3://releases.statika.ohnosequences.com")
      , "Statika public snapshots" at s3("s3://snapshots.statika.ohnosequences.com")
      )

    , resolvers <++= statikaResolvers { _ map s3ToMvn }

    // private resolvers

    , statikaPrivateResolvers := Seq(
        "Statika private releases"  at s3("s3://private.releases.statika.ohnosequences.com")
      , "Statika private snapshots" at s3("s3://private.snapshots.statika.ohnosequences.com")
      )

    , resolvers <++= (s3credentials, statikaPrivateResolvers) { (cs, rs) => 
        rs map { r => cs map s3resolver(r.name, r.bucket.toString) } flatten
      }

    // publishing

    , publishTo <<= (isSnapshot, s3credentials, isPrivate) { 
                      (snapshot,   credentials,   priv) => 
        val privacy = if (priv) "private." else ""
        val prefix = if (snapshot) "snapshots" else "releases"
        credentials map s3resolver( 
            "Statika "+privacy+prefix+" S3 publishing bucket"
          , "s3://"+privacy+prefix+".statika.ohnosequences.com"
          )
      }

    // scalac options

    , scalacOptions ++= Seq(
        "-feature"
      , "-language:higherKinds"
      , "-language:implicitConversions"
      , "-deprecation"
      , "-unchecked"
      )

    // general settings

    , organization := "ohnosequences"
    , scalaVersion := "2.10.2"
    , statikaVersion := "0.12.0-SNAPSHOT"

    // dependencies

    , libraryDependencies <++= statikaVersion { sv =>
        Seq (
          "com.chuusai" %% "shapeless" % "1.2.+"
        , "ohnosequences" %% "statika" % sv
        , "org.scalatest" %% "scalatest" % "1.9.1" % "test"
        )
      }

    // sbt-buildinfo plugin

    , bundleObject := ""
    , bundlePackage <<= (organization){_+".statika"}

    , sourceGenerators in Compile <++= (bundlePackage, bundleObject, buildInfo) { 
          (bp, bo, bi)  =>  
        if (bp.isEmpty || bo.isEmpty) Seq() else Seq(bi)
      }
    , buildInfoKeys <<= (name, bundlePackage, bundleObject, statikaResolvers, statikaPrivateResolvers) { 
        (name, pkg, obj, sResolvers, sPrivResolvers) =>
        Seq[BuildInfoKey](
          organization
        , "artifact" -> name
        , version
        , statikaVersion
        , "name" -> (pkg+"."+obj)
        , "resolvers" -> sResolvers
        , "privateResolvers" -> (sPrivResolvers map (_.bucket.toString))
        )
      }
    , buildInfoPackage <<= bundlePackage { _+".meta"}
    , buildInfoObjectFormat <<= (bundlePackage, bundleObject) { (bp, bo) =>
        "object %s extends ohnosequences.statika.MetaData.MetaDataOf["+bp+"."+bo+".type]"
      }
    , buildInfoObject <<= bundleObject
    ) 

    // sbt-release plugin

    releaseProcess <<= thisProjectRef apply { ref =>
      Seq[ReleaseStep](
        checkSnapshotDependencies
      , inquireVersions
      , runTest
      , setReleaseVersion
      , commitReleaseVersion
      , tagRelease
      , publishArtifacts
      , setNextVersion
      , pushChanges
      )
    }

}
