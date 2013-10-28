package ohnosequences.sbt

import sbt._
import Keys._

import com.typesafe.sbt.SbtStartScript._

import ohnosequences.sbt.SbtS3Resolver._
import ohnosequences.sbt.Era7SbtRelease._
import ohnosequences.sbt.statika.Utils._

import sbtassembly._
import sbtassembly.Plugin._
import AssemblyKeys._

object SbtStatikaPlugin extends sbt.Plugin {

  lazy val statikaVersion = settingKey[String]("Statika library version")
  lazy val publicResolvers = settingKey[Seq[Resolver]]("Public S3 resolvers for the bundle dependencies")
  lazy val privateResolvers = settingKey[Seq[S3Resolver]]("Private S3 resolvers for the bundle dependencies")

  lazy val awsStatikaVersion = settingKey[String]("AWS-Statika library version")
  lazy val metadataObject = settingKey[String]("Name of the generated metadata object")

  //////////////////////////////////////////////////////////////////////////////

  override def projectSettings: Seq[Setting[_]] = statikaSettings

  lazy val statikaSettings: Seq[Setting[_]] = 
    (startScriptForClassesSettings: Seq[Setting[_]]) ++ 
    (Era7.allSettings: Seq[Setting[_]]) ++ Seq(

    // resolvers needed for statika dependency
      resolvers ++= Seq ( 
        "Era7 public maven releases"  at toHttp("s3://releases.era7.com")
      , "Era7 public maven snapshots" at toHttp("s3://snapshots.era7.com")
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

    // adding privateResolvers to normal ones, if we have credentials
    , resolvers ++= 
          publicResolvers.value ++
        { privateResolvers.value map { r => s3credentials.value map r.toSbtResolver } flatten }

    // publishing (ivy-style by default)
    , publishMavenStyle := false
    , publishBucketSuffix := bucketSuffix.value
    // disable publishing sources and docs
    , publishArtifact in (Compile, packageSrc) := false
    , publishArtifact in (Compile, packageDoc) := false

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

    , statikaVersion := "0.17.0"

    // dependencies
    , libraryDependencies ++= Seq (
        "ohnosequences" %% "statika" % statikaVersion.value
      , "org.scalatest" %% "scalatest" % "1.9.2" % "test"
      )
    )

  lazy val distributionSettings: Seq[Setting[_]] = 
    (assemblySettings: Seq[Setting[_]]) ++ Seq[Setting[_]](

      awsStatikaVersion := "0.4.0"
    , libraryDependencies ++= Seq(
        "ohnosequences" %% "aws-statika" % awsStatikaVersion.value
      )

    // metadata generation
    , metadataObject := name.value.split("""\W""").map(_.capitalize).mkString
    , sourceGenerators in Compile += task[Seq[File]] {
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
          if(isPrivate.value) Seq() else Seq(toPublic(publishS3Resolver.value))
        }
        val privResolvers = privateResolvers.value ++ {
          if(isPrivate.value) Seq(publishS3Resolver.value) else Seq()
        }

        val text = """
          |package generated.metadata
          |
          |import ohnosequences.statika.aws._
          |
          |class $project$(
          |  val organization     : String = "$organization$"
          |, val artifact         : String = "$artifact$"
          |, val version          : String = "$version$"
          |, val resolvers        : Seq[String] = $resolvers$
          |, val privateResolvers : Seq[String] = $privateResolvers$
          |) extends SbtMetadata
          |""".stripMargin.
            replace("$project$", metadataObject.value).
            replace("$organization$", organization.value).
            replace("$artifact$", name.value.toLowerCase).
            replace("$version$", version.value).
            replace("$resolvers$", seqToStr(pubResolvers map resolverToString flatten)).
            replace("$privateResolvers$", seqToStr(privResolvers map (_.toString)))

        val file = (sourceManaged in Compile).value / "metadata.scala" 
        IO.write(file, text)
        Seq(file)
      }

    // publishing also a fat artifact:
    , artifact in (Compile, assembly) ~= { art =>
        art.copy(`classifier` = Some("fat"))
      }
    , test in assembly := {}
    ) ++ addArtifact(artifact in (Compile, assembly), assembly)

}
