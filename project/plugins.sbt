resolvers += "Era7 maven releases"  at "http://releases.era7.com.s3.amazonaws.com"

resolvers += "Era7 maven snapshots"  at "http://snapshots.era7.com.s3.amazonaws.com"

addSbtPlugin("ohnosequences" % "era7-sbt-settings" % "0.2.0-SNAPSHOT")
