import sbt._
import play.sbt.PlayImport

object Dependencies {

  //versions
  val awsClientVersion = "1.9.30"
  //libraries
  val scalaUri = "com.netaporter" %% "scala-uri" % "0.4.6"
  val identityCookie = "com.gu.identity" %% "identity-cookie" % "3.44"
  val identityPlayAuth = "com.gu.identity" %% "identity-play-auth" % "0.10"
  val playWS = PlayImport.ws
  val playCache = PlayImport.cache
  val playFilters = PlayImport.filters
  val scalaTestPlus =  "org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
  val specs2 = PlayImport.specs2 % "test"
  val awsWrap = "com.github.dwhjames" %% "aws-wrap" % "0.7.2"
  val awsSns =  "com.amazonaws" % "aws-java-sdk-sns" % "1.9.3"
  val awsCloudWatch = "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.9.31"
  val scalaz = "org.scalaz" %% "scalaz-core" % "7.1.1"
  val reactiveMongo = "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24"
  val salat = "com.novus" %% "salat" % "1.9.9"
  val embeddedMongo = "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.2" % "test"
  val emailValidation = "uk.gov.hmrc" %% "emailaddress" % "1.0.0"
  val wireMock = "com.github.tomakehurst" % "wiremock" % "1.57" % "test"
  val scalaTest = "org.scalatest" %% "scalatest" % "2.2.5" % "test"
  val autoMongoDiscovery = "com.gu" %% "mongodb-auto-discovery" % "1.6"
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.4.1" % "test"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.4.1" % "test"
  //projects

  val apiDependencies = Seq(scalaUri, identityCookie, identityPlayAuth, emailValidation,
    playWS, playCache, playFilters, awsWrap, awsCloudWatch, scalaz, reactiveMongo, salat,
    specs2, scalaTest, embeddedMongo, wireMock, scalaTestPlus, autoMongoDiscovery, akkaSlf4j, akkaTestkit)

}
