resolvers ++= Seq(
  "Era7 maven releases"  at "http://releases.era7.com.s3.amazonaws.com"
, "Era7 maven snapshots"  at "http://snapshots.era7.com.s3.amazonaws.com"
, Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.6.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.7.1")
// addSbtPlugin("ohnosequences" % "sbt-release" % "0.8-SNAPSHOT")

addSbtPlugin("net.virtual-void" % "sbt-cross-building" % "0.8.1-SNAPSHOT")
