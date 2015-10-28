import Dependencies._
import PlayArtifact._
import play.sbt.PlayScala
import play.sbt.routes.RoutesKeys._
import sbt.Keys._
import sbt._
import sbtbuildinfo.Plugin._
import com.typesafe.sbt.SbtNativePackager._

trait IdentityAdminApi {

  val appVersion = "1.0-SNAPSHOT"

  def buildInfoPlugin = buildInfoSettings ++ Seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      BuildInfoKey.constant("buildNumber", Option(System.getenv("BUILD_NUMBER")) getOrElse "DEV"),
      BuildInfoKey.constant("buildTime", System.currentTimeMillis),
      BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
        "git rev-parse HEAD".!!.trim
      } catch {
        case e: Exception => "unknown"
      }))
    ),
    buildInfoPackage := "app"
  )

  val commonSettings = Seq(
    organization := "com.gu",
    version := appVersion,
    scalaVersion := "2.11.6",
    resolvers ++= Seq(
      "Guardian Github Releases" at "https://guardian.github.io/maven/repo-releases",
      "Guardian Github Snapshots" at "http://guardian.github.com/maven/repo-snapshots",
      "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
      Resolver.typesafeRepo("releases"),
      Resolver.bintrayRepo("dwhjames", "maven"),
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.sonatypeRepo("releases")),
    sources in (Compile,doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    parallelExecution in Global := false,
    updateOptions := updateOptions.value.withCachedResolution(true),
    javaOptions in Test += "-Dconfig.resource=TEST.conf"
  ) ++ buildInfoPlugin


  def lib(name: String) = Project(name, file(name)).enablePlugins(PlayScala).settings(commonSettings: _*)

  def app(name: String) = lib(name)
    .settings(playArtifactDistSettings: _*)
    .settings(magentaPackageName := name)
    .settings(
      mappings in Universal ++=
        (baseDirectory.value / "deploy" * "*" get) map
          (x => x -> ("deploy/" + x.getName))
    )
}

object IdentityAdminApi extends Build with IdentityAdminApi {
  val api = app("identity-admin-api")
    .settings(libraryDependencies ++= apiDependencies)
    .settings(routesGenerator := InjectedRoutesGenerator)
    .settings(
      addCommandAlias("devrun", "run -Dconfig.resource=DEV.conf 9500")
    )


  val root = Project("root", base=file(".")).aggregate(api)
}
