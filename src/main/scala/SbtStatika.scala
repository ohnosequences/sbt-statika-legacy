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

object SbtStatikaPlugin extends Plugin {

  lazy val bundleObjects = SettingKey[Seq[String]]("bundle-objects",
    "Fully qualified names of the defined in code bundle objects")

  lazy val isPrivate = SettingKey[Boolean]("is-private", 
    "If true, publish to private S3 bucket, else to public")

  lazy val statikaVersion = SettingKey[String]("statika-version",
    "Statika library version")


  // AWS-specific keys:

  lazy val publicResolvers = SettingKey[Seq[Resolver]]("public-resolvers",
    "Public S3 resolvers for the bundle dependencies")

  lazy val privateResolvers = SettingKey[Seq[S3Resolver]]("private-resolvers",
    "Private S3 resolvers for the bundle dependencies")

  lazy val bucketSuffix = SettingKey[String]("bucket-suffix",
    "Amazon S3 bucket suffix for resolvers")

  lazy val publishBucketSuffix = SettingKey[String]("publish-bucket-suffix",
    "Amazon S3 bucket suffix for publish-to resolver")

  //////////////////////////////////////////////////////////////////////////////

  // generating metadata sourcecode
  private def metadataFile(
      sourceManaged: File
    , bundleObjects: Seq[String]
    , name: String
    , organization: String
    , version: String
    , statikaVersion: String
    , resolvers: Seq[Resolver]
    , publicResolvers: Seq[Resolver]
    , privateResolvers: Seq[S3Resolver]
    ): Seq[File] = { 

      def seqToStr(rs: Seq[String]) = 
        if (rs.isEmpty) "Seq()"  
        else rs.mkString("Seq(\"", "\", \"", "\")")

      // common sbt metadata is added to each object
      val header = """
        package generated.metadata

        import ohnosequences.statika._
      """

      val commonPart = """
          val organization = "%s"
          val artifact = "%s"
          val version = "%s"
          val statikaVersion = "%s"
          val resolvers = %s
          val privateResolvers = %s
        """ format (
          organization
        , name.toLowerCase
        , version
        , statikaVersion
        , seqToStr(((resolvers ++ publicResolvers) map resolverToString) flatten)
        , seqToStr(privateResolvers map (_.toString))
        )

      // NOTE: this was aimed to fix the metadata generation for case classes, but we can't use 
      //       them anyway, so may be better to remove this.
      def cleanName(n: String) = 
        if (n.endsWith("()")) {
          val nn = n.stripSuffix("()")
          (nn.split('.').last, nn, nn)
        } else 
          (n.split('.').last, n+".type", n)

      // the name of metadata object is the last part of bundle object name
      val metaObjects = bundleObjects map { obj => 
        val name = cleanName(obj)
        """
        object %s extends MetadataOf[%s] {
          val name = "%s"
          %s
        }
        """ format (name._1, name._2, name._3, commonPart)
      }

      // if there are no objects, don't generate anything
      if (metaObjects.isEmpty) Seq()
      else { 
        // otherwise join generated text and write to a file
        val file = sourceManaged / "metadata.scala" 
        IO.write(file, header + metaObjects.mkString)
        Seq(file)
      }
    }


  // here we add default set of setting to the project
  override def projectSettings = 
    startScriptForClassesSettings ++
    releaseSettings ++
    Seq(

    // resolvers needed for statika dependency
      resolvers ++= Seq ( 
        "Era7 public maven releases"  at toHttp("s3://releases.era7.com")
      , "Era7 public maven snapshots" at toHttp("s3://snapshots.era7.com")
      // ivy
      , Resolver.url("Era7 public ivy releases", url(toHttp("s3://releases.era7.com")))(ivy)
      , Resolver.url("Era7 public ivy snapshots", url(toHttp("s3://snapshots.era7.com")))(ivy)
      ) 

    , bucketSuffix <<= organization {"statika."+_+".com"}

    , publicResolvers <<= bucketSuffix { suffix => Seq(
          Resolver.url("Statika public ivy releases", url(toHttp("s3://releases."+suffix)))(ivy)
        , Resolver.url("Statika public ivy snapshots", url(toHttp("s3://snapshots."+suffix)))(ivy)
        )
      }

    , privateResolvers <<= (isPrivate, bucketSuffix) { (priv, suffix) =>
        if (!priv) Seq() else Seq(
            S3Resolver("Statika private ivy releases",  "s3://private.releases."+suffix, ivy)
          , S3Resolver("Statika private ivy snapshots", "s3://private.snapshots."+suffix, ivy)
          )
      }

    , resolvers <++= publicResolvers

    // adding privateResolvers to normal ones, if we have credentials
    , resolvers <++= (s3credentials, privateResolvers) { (cs, rs) => 
        rs map { r => cs map r.toSbtResolver } flatten
      }


    // publishing (ivy-style by default)
    , isPrivate := false
    , publishMavenStyle := false
    , publishBucketSuffix <<= bucketSuffix
    , publishTo <<= (isSnapshot, s3credentials, isPrivate, publishMavenStyle, publishBucketSuffix) { 
                      (snapshot,   credentials,   priv,    mvnStyle,          suffix) => 
        val privacy = if (priv) "private." else ""
        val prefix = if (snapshot) "snapshots" else "releases"
        credentials map S3Resolver( 
            "Statika "+privacy+prefix+" S3 publishing bucket"
          , "s3://"+privacy+prefix+"."+suffix
          , if(mvnStyle) mvn else ivy
          ).toSbtResolver
      }


    // general settings
    , statikaVersion := "0.16.0-SNAPSHOT"

    , scalaVersion := "2.10.3"
    , scalacOptions ++= Seq(
        "-feature"
      , "-language:higherKinds"
      , "-language:implicitConversions"
      , "-language:postfixOps"
      , "-deprecation"
      , "-unchecked"
      )


    // dependencies
    , libraryDependencies <++= statikaVersion { sv =>
        Seq (
          "ohnosequences" %% "statika" % sv
        , "org.scalatest" %% "scalatest" % "1.9.2" % "test"
        )
      }
    // 2.10.x are compatible and we want to use the latest for everything:
    , dependencyOverrides += "org.scala-lang" % "scala-library" % "2.10.3"

    // this doesn't allow any conflicts in dependencies:
    , conflictManager := ConflictManager.strict

    // metadata generation
    , bundleObjects := Seq()
    , sourceGenerators in Compile <+= (
          sourceManaged in Compile
        , bundleObjects
        , name
        , organization
        , version
        , statikaVersion
        , resolvers
        , publicResolvers
        , privateResolvers
        ) map metadataFile


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
