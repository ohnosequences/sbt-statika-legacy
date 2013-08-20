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

  lazy val privateResolvers = SettingKey[Seq[S3Resolver]]("private-resolvers",
    "Private S3 resolvers for statika dependencies")

  // convenience method, to use normal bucket addresses with `at`
  def http(bucket: String): String = 
    if(bucket.startsWith("s3://"))
       "http://"+bucket.stripPrefix("s3://")+".s3.amazonaws.com"
    else bucket


  // just a local aliases
  private val mvn = Resolver.mavenStylePatterns
  private val ivy = Resolver.ivyStylePatterns

  private def seqToString(s: Seq[String]): String = 
    s.mkString("Seq(\\\"", "\\\", \\\"", "\\\")")

  private def patternsToString(ps: Patterns): String =
    "Patterns(%s, %s, %s)" format (
      seqToString(ps.ivyPatterns)
    , seqToString(ps.artifactPatterns)
    , ps.isMavenCompatible
    )

  // TODO: write serializers for the rest of resolvers types
  private def resolverToString(r: Resolver): Option[String] = r match {
    case MavenRepository(name: String, root: String) => Some(
      """MavenRepository(\"%s\", \"%s\")""" format (name, root)
      )
    case URLRepository(name: String, patterns: Patterns) => Some(
      """URLRepository(\"%s\", %s)""" format 
        (name, patternsToString(patterns))
      )
    // case ChainedResolver(name: String, resolvers: Seq[Resolver]) => 
    // case FileRepository(name: String, configuration: FileConfiguration, patterns: Patterns) => 
    // case SshRepository(name: String, connection: SshConnection, patterns: Patterns, publishPermissions: Option[String]) => 
    // case SftpRepository(name: String, connection: SshConnection, patterns: Patterns) => 
    case _ => None
  }

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
      , "Era7 public maven releases"  at http("s3://releases.era7.com")
      , "Era7 public maven snapshots" at http("s3://snapshots.era7.com")
      , "Statika public maven releases" at http("s3://releases.statika.ohnosequences.com")
      , "Statika public maven snapshots" at http("s3://snapshots.statika.ohnosequences.com")
      // ivy
      , Resolver.url("Era7 public ivy releases", url(http("s3://releases.era7.com")))(ivy)
      , Resolver.url("Era7 public ivy snapshots", url(http("s3://snapshots.era7.com")))(ivy)
      , Resolver.url("Statika public ivy releases", url(http("s3://releases.statika.ohnosequences.com")))(ivy)
      , Resolver.url("Statika public ivy snapshots", url(http("s3://snapshots.statika.ohnosequences.com")))(ivy)
      )

    // private resolvers

    , privateResolvers := Seq(
        S3Resolver("Statika private ivy releases",   "s3://private.releases.statika.ohnosequences.com", ivy)
      , S3Resolver("Statika private maven releases", "s3://private.releases.statika.ohnosequences.com", mvn)
      , S3Resolver("Statika private ivy snapshots",   "s3://private.snapshots.statika.ohnosequences.com", ivy)
      , S3Resolver("Statika private maven snapshots", "s3://private.snapshots.statika.ohnosequences.com", mvn)
      )

    // adding privateResolvers to normal ones, if we have credentials
    , resolvers <++= (s3credentials, privateResolvers) { (cs, rs) => 
        rs map { r => cs map r.toSbtResolver } flatten
      }

    // publishing

    , publishMavenStyle := false

    , publishTo <<= (isSnapshot, s3credentials, isPrivate, publishMavenStyle) { 
                      (snapshot,   credentials,   priv,    mvnStyle) => 
        val privacy = if (priv) "private." else ""
        val prefix = if (snapshot) "snapshots" else "releases"
        credentials map S3Resolver( 
            "Statika "+privacy+prefix+" S3 publishing bucket"
          , "s3://"+privacy+prefix+".statika.ohnosequences.com"
          , if(mvnStyle) mvn else ivy
          ).toSbtResolver
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
    , statikaVersion := "0.12.2"

    // dependencies

    , libraryDependencies <++= statikaVersion { sv =>
        Seq (
          "ohnosequences" %% "statika" % sv
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
    , buildInfoKeys <<= (name, bundlePackage, bundleObject, resolvers, privateResolvers) { 
        (name, pkg, obj, resolvers, privateResolvers) =>
        Seq[BuildInfoKey](
          organization
        , "artifact" -> name.toLowerCase
        , version
        , statikaVersion
        , "name" -> (pkg+"."+obj)
        , "resolvers" -> ((resolvers map resolverToString).flatten)
        , "privateResolvers" -> privateResolvers
        )
      }
    , buildInfoPackage <<= bundlePackage { _+".meta"}
    , buildInfoObjectFormat <<= (bundlePackage, bundleObject) { (bp, bo) =>
        "object %s extends ohnosequences.statika.MetaDataOf["+bp+"."+bo+".type]"
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
