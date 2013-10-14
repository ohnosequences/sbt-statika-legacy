## Statika sbt-plugin

Default sbt project settings for statika bundles. This plugin is published for sbt `v0.12` and `v0.13`.

### Usage

Add the following dependency to `project/plugins.sbt` file in your sbt project

```scala
resolvers += "Era7 maven releases" at "http://releases.era7.com.s3.amazonaws.com"

addSbtPlugin("ohnosequences" % "sbt-statika" % "0.10.0")
```

#### Settings

Here is the list of sbt settings defined by this plugin (with their types and defaults):

* `statikaVersion: String = "0.15.0"` — Statika library version
* `bundleObjects: Seq[String] = Seq()` — Fully qualified names of the defined in code bundle objects for metadata generation
* `isPrivate: Boolean = false` — If true, publish to private S3 bucket, else to public (also adds private resolvers)

AWS-specific keys:

* `publicResolvers: Seq[Resolver]` — Public S3 resolvers for the bundle dependencies. Defaults are ivy resolvers for the following buckets: 
  + `"s3://releases." + bucketSuffix`
  + `"s3://snapshots." + bucketSuffix`
* `privateResolvers: Seq[S3Resolver]` — Private S3 resolvers for the bundle dependencies. Defaults are ivy **s3-resolvers** (see [sbt-s3-resolver](https://github.com/ohnosequences/sbt-s3-resolver) plugin) for the following buckets: 
  + `"s3://private.releases." + bucketSuffix`
  + `"s3://private.snapshots." + bucketSuffix`
* `bucketSuffix: String = "statika." + organization + ".com"` — Amazon S3 bucket suffix for resolvers. Note, that you should explicitly set `organization` key in your project
* `publishBucketSuffix: String = bucketSuffix` — Amazon S3 bucket suffix for publish-to resolver


#### Dependencies

This plugin adds to your project dependencies on

* [sbt-s3-resolver](https://github.com/ohnosequences/sbt-s3-resolver) plugin — for resolving from S3 buckets
* [sbt-start-script](https://github.com/sbt/sbt-start-script) plugin — for convenient running
* [sbt-release](https://github.com/ohnosequences/sbt-release) plugin (our fork of it) — for standardized release process
* [statika](https://github.com/ohnosequences/statika) library
* [scalatest](https://github.com/scalatest/scalatest) library
