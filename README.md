## Statika sbt-plugin

Default sbt project settings for statika bundles. This plugin is published for sbt `v0.12` and `v0.13`.

### Usage

Add the following dependency to `project/plugins.sbt` file in your sbt project

```scala
resolvers += "Era7 maven releases" at "http://releases.era7.com.s3.amazonaws.com"

addSbtPlugin("ohnosequences" % "sbt-statika" % "0.11.0")
```

#### Settings

Here is the list of sbt settings defined by this plugin (with their types and defaults):

* `statikaVersion: String = "0.15.0"` — Statika library version
* `bundleObjects: Seq[String] = Seq()` — Fully qualified names of the defined in code bundle objects for metadata generation
* `isPrivate: Boolean = false` — If true, publish to private S3 bucket, else to public (also adds private resolvers)

AWS-specific keys:

* `awsStatikaVersion: String` — AWS-Statika library version
* `metadataObject: String` — Name of the generated metadata object


#### Dependencies

This plugin adds to your project dependencies on

* [sbt-s3-resolver](https://github.com/ohnosequences/sbt-s3-resolver) plugin — for resolving from S3 buckets
* [sbt-start-script](https://github.com/sbt/sbt-start-script) plugin — for convenient running
* [sbt-release](https://github.com/ohnosequences/sbt-release) plugin (our fork of it) — for standardized release process
* [scalatest](https://github.com/scalatest/scalatest) library
* [statika](https://github.com/ohnosequences/statika) library
* [aws-statika](https://github.com/ohnosequences/aws-statika) library (if `awsStatikaVersion` is set)
