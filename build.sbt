import sbtrelease._
import sbt._
import Defaults._

releaseSettings


sbtPlugin := true

name := "sbt-statika"

organization := "ohnosequences"

description := "Default sbt project settings for statika bundles"


crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.12", "0.13")

scalacOptions <++= scalaVersion map { 
  case "2.10.2" => Seq(
      "-feature"
    , "-deprecation"
    , "-language:postfixOps"
    )
  case _ => Seq()
}


publishMavenStyle := true

publishTo <<= (isSnapshot, s3credentials) { 
                (snapshot,   credentials) => 
  val prefix = if (snapshot) "snapshots" else "releases"
  credentials map S3Resolver(
      "Era7 "+prefix+" S3 publishing bucket"
    , "s3://"+prefix+".era7.com"
    ).toSbtResolver
}

resolvers ++= Seq ( 
  "Era7 maven releases"  at "http://releases.era7.com.s3.amazonaws.com"
// , "Era7 maven snapshots" at "http://snapshots.era7.com.s3.amazonaws.com"   
// , Resolver.url(
//     "sbt-plugin-releases",
//     new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/")
//   )(Resolver.ivyStylePatterns)
)

// NOTE: instead of addSbtPlugin, we need to use sbtPluginExtra, to set the right sbt and scala versions explicitly (otherwise they are mixed up)
libraryDependencies <++= (sbtVersion in sbtPlugin, scalaBinaryVersion) { (sbtV, scalaV) => Seq[ModuleID](
    sbtPluginExtra("ohnosequences" % "sbt-s3-resolver" % "0.6.0", sbtV, scalaV)
  , sbtPluginExtra("com.github.gseitz" % "sbt-release" % "0.8",   sbtV, scalaV)
  , sbtPluginExtra("com.typesafe.sbt" % "sbt-start-script" % 
                     (if (sbtV == "0.13") "0.10.0" else "0.9.0"), sbtV, scalaV)
  )
}
// instead of usual:
//    addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.6.0")
//    addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.7.1")
//    addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.8.0")
