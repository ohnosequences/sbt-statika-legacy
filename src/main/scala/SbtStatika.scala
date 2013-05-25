import sbt._
import Keys._

import com.typesafe.sbt.SbtStartScript._
import sbtbuildinfo.Plugin._
import sbtrelease.ReleasePlugin._
import SbtS3Resolver._

object SbtStatika extends Plugin {

  def ivyResolver(name: String, addr: String): Resolver =
    Resolver.url(name, url(addr))(Resolver.ivyStylePatterns)

  lazy val isPrivate = SettingKey[Boolean]("is-private", 
    "If true, publish to private S3 bucket, else to public")

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

    // dependencies

    , libraryDependencies ++= Seq ( 
        "com.chuusai" %% "shapeless" % "1.2.3"
      , "ohnosequences" %% "statika" % "0.9.0"
      )

    // sbt-buildinfo plugin

    , sourceGenerators in Compile <+= buildInfo
    , buildInfoKeys <<= name { name => 
        Seq[BuildInfoKey]("artifact" -> name, version)
      }
    , buildInfoPrefix := """object MetaInfo { 
        import ohnosequences.statika.General.BundleMetaInfo
      """
    , buildInfoObject := "implicit object InfoImplicit extends BundleMetaInfo"
    , buildInfoSuffix := "}"
    ) 
}
