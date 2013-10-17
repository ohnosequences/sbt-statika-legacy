// package ohnosequences.statika.sbt

import sbt._
import Keys._

import com.typesafe.sbt.SbtStartScript._

import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

import SbtS3Resolver._
import Utils._

object SbtStatikaPlugin extends sbt.Plugin {

  lazy val metadataObject = settingKey[String]("Name of the generated metadata object")
  lazy val isPrivate = settingKey[Boolean]("If true, publish to private S3 bucket, else to public")
  lazy val statikaVersion = settingKey[String]("Statika library version")
  lazy val awsStatikaVersion = settingKey[String]("AWS-Statika library version")

  // AWS-specific keys:
  lazy val publicResolvers = settingKey[Seq[Resolver]]("Public S3 resolvers for the bundle dependencies")
  lazy val privateResolvers = settingKey[Seq[S3Resolver]]("Private S3 resolvers for the bundle dependencies")
  lazy val bucketSuffix = settingKey[String]("Amazon S3 bucket suffix for resolvers")
  lazy val publishBucketSuffix = settingKey[String]("Amazon S3 bucket suffix for publish-to resolver")
  lazy val publishResolver = settingKey[S3Resolver]("S3Resolver which will be used in publishTo")

  //////////////////////////////////////////////////////////////////////////////

  // here we add default set of setting to the project
  override def projectSettings = 
    // we need to set here the type explicitly, because of deprecation warning for `Setting` type
    (startScriptForClassesSettings: Seq[sbt.Def.Setting[_]]) ++ 
    (releaseSettings: Seq[sbt.Def.Setting[_]]) ++ 
    statikaSettings

  lazy val statikaSettings = Seq(

    // resolvers needed for statika dependency
      resolvers ++= Seq ( 
        "Era7 public maven releases"  at toHttp("s3://releases.era7.com")
      , "Era7 public maven snapshots" at toHttp("s3://snapshots.era7.com")
      // ivy
      , Resolver.url("Era7 public ivy releases", url(toHttp("s3://releases.era7.com")))(ivy)
      , Resolver.url("Era7 public ivy snapshots", url(toHttp("s3://snapshots.era7.com")))(ivy)
      ) 

    , bucketSuffix := {"statika." + organization.value + ".com"}

    , publicResolvers := Seq(
          Resolver.url("Statika public ivy releases", url(toHttp("s3://releases."+bucketSuffix.value)))(ivy)
        , Resolver.url("Statika public ivy snapshots", url(toHttp("s3://snapshots."+bucketSuffix.value)))(ivy)
        )

    , privateResolvers := {
        if (!isPrivate.value) Seq() else Seq(
            S3Resolver("Statika private ivy releases",  "s3://private.releases."+bucketSuffix.value, ivy)
          , S3Resolver("Statika private ivy snapshots", "s3://private.snapshots."+bucketSuffix.value, ivy)
          )
      }

    , resolvers ++= publicResolvers.value

    // adding privateResolvers to normal ones, if we have credentials
    , resolvers ++= {
        privateResolvers.value map { r => s3credentials.value map r.toSbtResolver } flatten
      }


    // publishing (ivy-style by default)
    , isPrivate := false
    , publishMavenStyle := false
    , publishBucketSuffix := bucketSuffix.value
    , publishResolver := {
        val privacy = if (isPrivate.value) "private." else ""
        val prefix = if (isSnapshot.value) "snapshots" else "releases"
        S3Resolver( 
          "Statika "+privacy+prefix+" S3 publishing bucket"
        ,    "s3://"+privacy+prefix+"."+publishBucketSuffix.value
        , if(publishMavenStyle.value) mvn else ivy
        )
      }
    , publishTo := { s3credentials.value map publishResolver.value.toSbtResolver }


    // this doesn't allow any conflicts in dependencies:
    , conflictManager := ConflictManager.strict

    , scalaVersion := "2.10.3"
    // 2.10.x are compatible and we want to use the latest _for everything_:
    , dependencyOverrides += "org.scala-lang" % "scala-library" % "2.10.3"

    , scalacOptions ++= Seq(
          "-feature"
        , "-language:higherKinds"
        , "-language:implicitConversions"
        , "-language:postfixOps"
        , "-deprecation"
        , "-unchecked"
        )

    , statikaVersion := "0.17.0-SNAPSHOT"
    , awsStatikaVersion := ""

    // dependencies
    , libraryDependencies ++= { Seq (
          "ohnosequences" %% "statika" % statikaVersion.value
        , "org.scalatest" %% "scalatest" % "1.9.2" % "test"
        ) ++ { // if awsStatikaVersion is empty (by default), no dependency:
        if (awsStatikaVersion.value.isEmpty) Seq() else
          Seq("ohnosequences" %% "aws-statika" % awsStatikaVersion.value) 
        }
      }

    // metadata generation
    , metadataObject := name.value.split("""\W""").map(_.capitalize).mkString
    , sourceGenerators in Compile += task[Seq[File]] {
        // if we are not using AWS stuff, we don't need metadata
        if (awsStatikaVersion.value.isEmpty) Seq()
        else {
          // helps to serialize Strings correctly:
          def seqToStr(rs: Seq[String]) = 
            if  (rs.isEmpty) "Seq()"  
            else rs.mkString("Seq(\"", "\", \"", "\")")

          // TODO: move it to the sbt-s3-resolver
          def toPublic(r: S3Resolver): Resolver = {
            if(publishMavenStyle.value) r.name at r.url
            else Resolver.url(r.name, url(toHttp(r.url)))(r.patterns)
          }
          // adding publishing resolver to the right list
          val pubResolvers = resolvers.value ++ {
            if(isPrivate.value) Seq() else Seq(toPublic(publishResolver.value))
          }
          val privResolvers = privateResolvers.value ++ {
            if(isPrivate.value) Seq(publishResolver.value) else Seq()
          }

          val text = """
            |package generated.metadata
            |
            |import ohnosequences.statika.aws._
            |
            |object $project$ extends SbtMetadata { 
            |  val organization     = "$organization$"
            |  val artifact         = "$artifact$"
            |  val version          = "$version$"
            |  val statikaVersion   = "$statikaVersion$"
            |  val resolvers        = $resolvers$
            |  val privateResolvers = $privateResolvers$
            |}""".stripMargin.
              replace("$project$", metadataObject.value).
              replace("$organization$", organization.value).
              replace("$artifact$", name.value.toLowerCase).
              replace("$version$", version.value).
              replace("$statikaVersion$", statikaVersion.value).
              replace("$resolvers$", seqToStr(pubResolvers map resolverToString flatten)).
              replace("$privateResolvers$", seqToStr(privResolvers map (_.toString)))

          val file = (sourceManaged in Compile).value / "metadata.scala" 
          IO.write(file, text)
          Seq(file)
        }
      }


    // sbt-release plugin
    , releaseProcess <<= thisProjectRef apply { ref =>
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

    )
}
