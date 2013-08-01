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

  override def settings = 
    startScriptForClassesSettings ++
    releaseSettings ++
    buildInfoSettings ++ 
    Seq(
    // resolvers

      resolvers ++= Seq ( 
        // maven:
        Resolver.typesafeRepo("releases")
      , Resolver.sonatypeRepo("releases")
      , Resolver.sonatypeRepo("snapshots")
      , "Era7 Releases"  at "http://releases.era7.com.s3.amazonaws.com"
      , "Era7 Snapshots" at "http://snapshots.era7.com.s3.amazonaws.com"
      , "Statika public snapshots" at "http://snapshots.statika.ohnosequences.com.s3.amazonaws.com"
      , "Statika public releases" at "http://releases.statika.ohnosequences.com.s3.amazonaws.com"
        // ivy:
      , Resolver.url("Era7 ivy snapshots", url("http://snapshots.era7.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      , Resolver.url("Era7 ivy releases",  url("http://releases.era7.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      , Resolver.url("Statika ivy public snapshots", url("http://snapshots.statika.ohnosequences.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      , Resolver.url("Statika ivy public releases",  url("http://releases.statika.ohnosequences.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      )

    // private resolvers

    , resolvers <++= s3credentials { cs => Seq(
          cs map s3resolver("Statika private snapshots", "s3://private.snapshots.statika.ohnosequences.com")
        , cs map s3resolver("Statika private releases",  "s3://private.releases.statika.ohnosequences.com")
        , cs map s3resolver("Statika ivy private snapshots", "s3://private.snapshots.statika.ohnosequences.com", Resolver.localBasePattern)
        , cs map s3resolver("Statika ivy private releases",  "s3://private.releases.statika.ohnosequences.com", Resolver.localBasePattern)
        ).flatten 
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
        // , "ohnosequences" % "gener8bundle_2.10.0" % "0.12.+" % "test"
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
    , buildInfoKeys <<= name { name =>
        Seq[BuildInfoKey](
          organization
        , "artifact" -> name
        , version
        , s3credentialsFile
        , statikaVersion
        , resolvers
        , BuildInfoKey.map(bundleObject) { case (k, v) => 
            "name" -> (v+""".getClass.getName.split("\\$").last""") 
          }
        )
      }
    , buildInfoPackage <<= bundlePackage
    , buildInfoPrefix := "object GeneratedMetaData {"
    , buildInfoObjectFormat <<= bundleObject { 
        "implicit object %s extends ohnosequences.statika.MetaData.MetaDataOf["+_+".type]"
      }
    , buildInfoObject <<= bundleObject { _+"MD" }
    , buildInfoSuffix := "}"
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
