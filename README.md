## Statika sbt-plugin

Default sbt project settings for statika bundles. This plugin is published for sbt `v0.12` and `v0.13`.

### Usage

Add the following dependency to `project/plugins.sbt` file in your sbt project

```scala
resolvers += "Era7 maven releases" at "http://releases.era7.com.s3.amazonaws.com"

addSbtPlugin("ohnosequences" % "sbt-statika" % "0.13.0")
```

### Settings

Here is the list of sbt settings defined by this plugin (see code for defaults):

 Key                 |     Type        | Description                                      
--------------------:|:----------------|:-------------------------------------------------
 `statikaVersion`    | String          | Version of statika library dependency            
 `awsStatikaVersion` | String          | Version of aws-statika library dependency        
 `publicResolvers`   | Seq[Resolver]   | Public S3 resolvers for the bundle dependencies  
 `privateResolvers`  | Seq[S3Resolver] | Private S3 resolvers for the bundle dependencies 
 `metadataObject`    | String          | Name of the generated metadata object            

See also settings from [era7-sbt-release](https://github.com/ohnosequences/era7-sbt-release/) plugin.


### Dependencies

This plugin adds to your project dependencies on

* [era7-sbt-release](https://github.com/ohnosequences/era7-sbt-release) plugin for standardized release process
* [sbt-start-script](https://github.com/sbt/sbt-start-script) plugin for convenient running
* [scalatest](https://github.com/scalatest/scalatest) library
* [statika](https://github.com/ohnosequences/statika) library
* [aws-statika](https://github.com/ohnosequences/aws-statika) library (if `awsStatikaVersion` is set)
