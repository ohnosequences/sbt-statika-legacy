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

  lazy val bundleAmi = SettingKey[String]("bundle-ami",
    "Name of AMI bundle object (namespace)")

  lazy val bundlePackage = SettingKey[String]("bundle-object",
    "Package name for the bundle")

  lazy val bundleObject = SettingKey[String]("bundle-package",
    "Supposed name of the bundle object")

  lazy val isPrivate = SettingKey[Boolean]("is-private", 
    "If true, publish to private S3 bucket, else to public")

  lazy val statikaVersion = SettingKey[String]("statika-version",
    "statika library version")

  lazy val genBuildInfo = SettingKey[Boolean]("gen-buildinfo",
    "If false, no buildinfo settings will be used")

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
        // ivy:
      , Resolver.url("Era7 ivy snapshots", url("http://snapshots.era7.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      , Resolver.url("Era7 ivy releases",  url("http://releases.era7.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      , Resolver.url("Statika public snapshots", url("http://snapshots.statika.ohnosequences.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      , Resolver.url("Statika public releases",  url("http://releases.statika.ohnosequences.com.s3.amazonaws.com"))(Resolver.ivyStylePatterns)
      )

    // private resolvers

    , resolvers <++= s3credentials { cs => Seq(
          cs map s3resolver("Statika private snapshots", "s3://private.snapshots.statika.ohnosequences.com")
        , cs map s3resolver("Statika private releases",  "s3://private.releases.statika.ohnosequences.com")
        ).flatten 
      }

    // publishing

    , isPrivate := true
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

    , scalaVersion := "2.10.2"
    , statikaVersion := "0.11.1"
    , organization := "ohnosequences"

    // dependencies

    , libraryDependencies <++= statikaVersion { sv =>
        Seq (
          "com.chuusai" %% "shapeless" % "1.2.3"
        , "ohnosequences" %% "statika" % sv
        , "ohnosequences" % "gener8bundle_2.10.0" % "0.9.0" % "test"
        , "org.scalatest" %% "scalatest" % "1.9.1" % "test"
        )
      }

    // sbt-buildinfo plugin

    , genBuildInfo := true
    , bundlePackage <<= (organization, bundleAmi) { (o,a) => 
        o+".statika"+( if (a.isEmpty) "" else "."+a )
      }

    , sourceGenerators in Compile <++= (genBuildInfo, buildInfo) { (gen, bi)  =>  
        if (gen) Seq(bi) else Seq() 
      }
    , buildInfoKeys <<= name { name =>
        Seq[BuildInfoKey](
          "artifact" -> name
        , version
        , s3credentialsFile
        , statikaVersion
        , organization
        )
      }
    , buildInfoPackage <<= bundlePackage
    , buildInfoPrefix := "object MetaData {"
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
