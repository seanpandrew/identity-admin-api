lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)

name := "identity-admin-api"
organization := "com.gu"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.8"

buildInfoKeys := Seq[BuildInfoKey](
  name,
  BuildInfoKey.constant("buildNumber", Option(System.getenv("BUILD_NUMBER")) getOrElse "DEV"),
  BuildInfoKey.constant("buildTime", System.currentTimeMillis),
  BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
    "git rev-parse HEAD".!!.trim
  } catch {
    case e: Exception => "unknown"
  }))
)
buildInfoOptions += BuildInfoOption.ToMap
buildInfoPackage := "app"

resolvers ++= Seq(
  "Guardian Github Releases" at "https://guardian.github.io/maven/repo-releases",
  "Guardian Github Snapshots" at "http://guardian.github.com/maven/repo-snapshots",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  Resolver.typesafeRepo("releases"),
  Resolver.bintrayRepo("dwhjames", "maven"),
  Resolver.bintrayRepo("guardian", "platforms"),
  Resolver.bintrayRepo("hmrc", "releases"),
  Resolver.sonatypeRepo("releases"))
libraryDependencies ++= Dependencies.apiDependencies

sources in (Compile,doc) := Seq.empty
publishArtifact in (Compile, packageDoc) := false
parallelExecution in Global := false
updateOptions := updateOptions.value.withCachedResolution(true)
javaOptions in Test += "-Dconfig.resource=TEST.conf"

PlayArtifact.playArtifactDistSettings
mappings in Universal ++= (baseDirectory.value / "deploy" * "*" get) map (x => x -> ("deploy/" + x.getName))
magentaPackageName := name.value


addCommandAlias("devrun", "run -Dconfig.resource=DEV.conf 9500")

