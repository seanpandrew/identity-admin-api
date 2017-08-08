import sbt._
import play.sbt.PlayImport

object Dependencies {
  val playWS =        PlayImport.ws
  val playCache =     PlayImport.cache
  val playFilters =   PlayImport.filters
  val specs2 =        PlayImport.specs2   % "test"

  val scalaUri =            "com.netaporter"                %% "scala-uri"                % "0.4.16"
  val identityCookie =      "com.gu.identity"               %% "identity-cookie"          % "3.78"
  val identityPlayAuth =    "com.gu.identity"               %% "identity-play-auth"       % "1.2"
  val awsWrap =             "com.github.dwhjames"           %% "aws-wrap"                 % "0.8.0"
  val aws =                 "com.amazonaws"                 %  "aws-java-sdk"             % "1.11.105"
  val scalaz =              "org.scalaz"                    %% "scalaz-core"              % "7.2.10"
  val reactiveMongo =       "org.reactivemongo"             %% "play2-reactivemongo"      % "0.12.4"
  val emailValidation =     "uk.gov.hmrc"                   %% "emailaddress"             % "2.1.0"
  val exactTargetFuel =     "com.exacttarget"               %  "fuelsdk"                  % "1.1.0"
  val tip =                 "com.gu"                        %% "tip"                      % "0.3.2"
  val diff =                "ai.x"                          %% "diff"                     % "1.2.0"
  val scalaTestPlus =       "org.scalatestplus.play"        %% "scalatestplus-play"       % "2.0.0"     % "test"
  val embeddedMongo =       "com.github.simplyscala"        %% "scalatest-embedmongo"     % "0.2.3"     % "test"
  val mockWs =              "de.leanovate.play-mockws"      %% "play-mockws"              % "2.5.1"     % "test"
  val scalaTest =           "org.scalatest"                 %% "scalatest"                % "3.0.1"     % "test"
  val akkaSlf4j =           "com.typesafe.akka"             %% "akka-slf4j"               % "2.4.17"    % "test"
  val akkaTestkit =         "com.typesafe.akka"             %% "akka-testkit"             % "2.4.17"    % "test"
  val slf4jTesting =        "com.portingle"                 % "slf4jtesting"	            % "1.1.3"     % "test"

  val apiDependencies = Seq(scalaUri, identityCookie, identityPlayAuth, emailValidation,
    playWS, playCache, playFilters, awsWrap, scalaz, reactiveMongo,
    specs2, scalaTest, embeddedMongo, mockWs, scalaTestPlus, akkaSlf4j, akkaTestkit,
    exactTargetFuel, tip, aws, diff, slf4jTesting)

}
