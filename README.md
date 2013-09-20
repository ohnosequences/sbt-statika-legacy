## Statika sbt-plugin

Default sbt project settings for statika bundles. This plugin is published for sbt `v0.12` and `v0.13`.

### Usage

Add the following dependency to `project/plugins.sbt` file in your sbt project

```scala
resolvers += "Era7 maven releases" at "http://releases.era7.com.s3.amazonaws.com"

libraryDependencies += "ohnosequences" %% "sbt-statika" % "0.9.0"
```

#### Settings

This plugin defines some settings, that can be useful in your project:

* `statikaVersion` — Statika library version
* `bundleObjects` — Fully qualified names of the defined in code bundle objects for metadata generation
* `isPrivate` — If true, publish to private S3 bucket, else to public (also adds private resolvers)

AWS-specific keys:

* `publicResolvers` — Public S3 resolvers for the bundle dependencies
* `privateResolvers` — Private S3 resolvers for the bundle dependencies
* `bucketSuffix` — Amazon S3 bucket suffix for resolvers
* `publishBucketSuffix` — Amazon S3 bucket suffix for publish-to resolver

Note, that you should explicitly set `organization` key in your project.


#### Dependencies

This plugin adds to your project dependencies on

* [sbt-s3-resolver](https://github.com/ohnosequences/sbt-s3-resolver) plugin — for resolving from S3 buckets
* [sbt-start-script](https://github.com/sbt/sbt-start-script) plugin — for convenient running
* [sbt-release](https://github.com/ohnosequences/sbt-release) plugin (our fork of it) — for standardized release process
* [statika](https://github.com/ohnosequences/statika) library
* [scalatest](https://github.com/scalatest/scalatest) library
