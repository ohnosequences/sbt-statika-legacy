// package ohnosequences.statika.sbt

import sbt._
import Keys._

import com.typesafe.sbt.SbtStartScript._

import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

import SbtS3Resolver._

trait SbtStatikaPlugin extends Plugin {

  lazy val bundleObjects = SettingKey[Seq[String]]("bundle-objects",
    "Fully qualified names of the defined in code bundle objects")

  lazy val isPrivate = SettingKey[Boolean]("is-private", 
    "If true, publish to private S3 bucket, else to public")

  lazy val statikaVersion = SettingKey[String]("statika-version",
    "Statika library version")

  lazy val publicResolvers = SettingKey[Seq[Resolver]]("public-resolvers",
    "Public S3 resolvers for the bundle dependencies")

  lazy val privateResolvers = SettingKey[Seq[S3Resolver]]("private-resolvers",
    "Private S3 resolvers for the bundle dependencies")


  // just some local aliases
  private val mvn = Resolver.mavenStylePatterns
  private val ivy = Resolver.ivyStylePatterns

  private def seqToString(s: Seq[String]): String = 
    if (s.isEmpty) "Seq()"
    else s.mkString("Seq(\\\"", "\\\", \\\"", "\\\")")

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

      // common sbt metadata we define separately and then mix to each object
      val commonPart = """
        package generated.metadata

        import ohnosequences.statika._

        trait CommonMetaData extends MetaData {
          val organization = "%s"
          val artifact = "%s"
          val version = "%s"
          val statikaVersion = "%s"
          val resolvers = %s
          val privateResolvers = %s
        } 
        """ format (
          name.toLowerCase
        , organization
        , version
        , statikaVersion
        , seqToStr(((resolvers ++ publicResolvers) map resolverToString) flatten)
        , seqToStr(privateResolvers map (_.toString))
        )

      // the name of metadata object is the last part of bundle object name
      val metaObjects = bundleObjects map { obj => """
        object %s extends MetaDataOf[%s.type] with CommonMetaData {
          val name = "%s"
        }
        """ format (obj.split('.').last, obj, obj)
      }

      // if there are no objects, don't generate anything
      if (metaObjects.isEmpty) Seq()
      else { 
        // otherwise join generated text and write to a file
        val file = sourceManaged / "metadata.scala" 
        IO.write(file, commonPart + metaObjects.mkString)
        Seq(file)
      }
    }


  // here we add default set of setting to the project
  def sbtStatikaSettings: Seq[Setting[_]] = 
    startScriptForClassesSettings ++
    releaseSettings ++
    Seq(

    // resolvers needed for statika dependency
      resolvers ++= Seq ( 
      //   Resolver.typesafeRepo("releases")
      // , Resolver.sonatypeRepo("releases")
      // , Resolver.sonatypeRepo("snapshots")
        "Era7 public maven releases"  at toHttp("s3://releases.era7.com")
      , "Era7 public maven snapshots" at toHttp("s3://snapshots.era7.com")
      // ivy
      , Resolver.url("Era7 public ivy releases", url(toHttp("s3://releases.era7.com")))(ivy)
      , Resolver.url("Era7 public ivy snapshots", url(toHttp("s3://snapshots.era7.com")))(ivy)
      ) 

    , publicResolvers := Seq()
    , privateResolvers := Seq()

    , resolvers <++= publicResolvers

    // adding privateResolvers to normal ones, if we have credentials
    , resolvers <++= (s3credentials, privateResolvers) { (cs, rs) => 
        rs map { r => cs map r.toSbtResolver } flatten
      }


    // general settings
    , scalaVersion := "2.10.2"
    , scalacOptions ++= Seq(
        "-feature"
      , "-language:higherKinds"
      , "-language:implicitConversions"
      , "-deprecation"
      , "-unchecked"
      )


    // dependencies
    , libraryDependencies <++= statikaVersion { sv =>
        Seq (
          "ohnosequences" %% "statika" % sv
        , "org.scalatest" %% "scalatest" % "1.9.1" % "test"
        )
      }


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
