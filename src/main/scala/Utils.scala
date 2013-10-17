// package ohnosequences.statika.sbt

object Utils {

  import sbt._
  import Keys._
  import SbtS3Resolver._

  // just some local aliases
  val mvn = Resolver.mavenStylePatterns
  val ivy = Resolver.ivyStylePatterns

  def seqToString(s: Seq[String]): String = 
    if (s.isEmpty) "Seq()"
    else s.mkString("Seq(\\\"", "\\\", \\\"", "\\\")")

  def patternsToString(ps: Patterns): String =
    "Patterns(%s, %s, %s)" format (
      seqToString(ps.ivyPatterns)
    , seqToString(ps.artifactPatterns)
    , ps.isMavenCompatible
    )

  // TODO: write serializers for the rest of resolvers types
  def resolverToString(r: Resolver): Option[String] = r match {
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

  // TODO: move it to the sbt-s3-resolver
  def publicS3toSbtResolver(r: S3Resolver): Resolver = {
    if(r.patterns == mvn) r.name at r.url
    else Resolver.url(r.name, url(toHttp(r.url)))(r.patterns)
  }

}
