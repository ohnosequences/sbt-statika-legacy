import sbt._
import Keys._

import com.typesafe.sbt.SbtStartScript._
import sbtbuildinfo.Plugin._
import sbtrelease.ReleasePlugin._
import SbtS3Resolver._

object SbtStatika extends Plugin {

  def ivyResolver(name: String, addr: String): Resolver =
    Resolver.url(name, url(addr))(Patterns("[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"))

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
      , ivyResolver("Era7 ivy snapshots", "http://snapshots.era7.com.s3.amazonaws.com")
      , ivyResolver("Era7 ivy releases",  "http://releases.era7.com.s3.amazonaws.com")
      , ivyResolver("Statika public snapshots", "http://snapshots.statika.ohnosequences.com.s3.amazonaws.com")
      , ivyResolver("Statika public releases",  "http://releases.statika.ohnosequences.com.s3.amazonaws.com")
      )

    // private resolvers

    , resolvers <++= s3resolver { s3 => Seq(
          s3("Statika private snapshots", "s3://private.snapshots.statika.ohnosequences.com")
        , s3("Statika private releases",  "s3://private.releases.statika.ohnosequences.com")
        ).flatten 
      }

    // publishing

    , publishMavenStyle := false
    , isPrivate := true
    , publishTo <<= (isSnapshot, s3resolver, isPrivate) { 
                      (snapshot,   resolver,   priv) => 
        val privacy = if (priv) "private." else ""
        val prefix = if (snapshot) "snapshots" else "releases"
        resolver( "Statika "+privacy+prefix+" S3 publishing bucket"
                , "s3://"+privacy+prefix+".statika.ohnosequences.com")
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

    , scalaVersion := "2.10.0"
    , statikaVersion := "0.11.0"
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
}
