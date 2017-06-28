package repositories

import javax.inject.{Inject, Singleton}
import com.gu.identity.util.Logging
import models.{ApiError, ApiResponse, ReservedUsername, ReservedUsernameList}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.play.json.collection._
import reactivemongo.play.json._
import scalaz.{-\/, OptionT, \/-}
import scalaz.std.scalaFuture._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton class ReservedUserNameWriteRepository @Inject() (
    environment: play.api.Environment,
    reactiveMongoApi: ReactiveMongoApi) extends Logging {

  private lazy val reservedUsernamesF = reactiveMongoApi.database.map(_.collection("reservedUsernames"))

  def findReservedUsername(query: String): ApiResponse[ReservedUsername] =
    OptionT(reservedUsernamesF.flatMap(_.find(buildSearchQuery(query)).one[ReservedUsername])).fold(
      username => \/-(username),
      -\/(ApiError("Username not found"))
    )

  private def buildSearchQuery(query: String) =
    Json.obj(
      "$or" -> Json.arr(
        Json.obj("_id" -> query.toLowerCase),
        Json.obj("primaryEmailAddress" -> query.toLowerCase),
        Json.obj("username" -> query)
      )
    )

  def removeReservedUsername(username: String): ApiResponse[ReservedUsernameList] =
    reservedUsernamesF.flatMap(_.remove(buildSearchQuery(username))).flatMap(_ => loadReservedUsernames)
      .recover { case error => -\/(ApiError(s"Failed to remove reserved username $username", error.getMessage)) }

  def loadReservedUsernames: ApiResponse[ReservedUsernameList] =
    reservedUsernamesF.flatMap {
      _.find(Json.obj())
        .sort(Json.obj("username" -> 1))
        .cursor[ReservedUsername](ReadPreference.primaryPreferred)
        .collect[List](-1, Cursor.FailOnError[List[ReservedUsername]]())
    }.map(_.map(_.username)).map(ReservedUsernameList(_)).map(\/-(_))
      .recover { case error => -\/(ApiError("Failed to load reserved usernames list", error.getMessage)) }

  def addReservedUsername(username: String): ApiResponse[ReservedUsernameList] =
    reservedUsernamesF.flatMap(_.insert[ReservedUsername](ReservedUsername(username))).flatMap(_ => loadReservedUsernames)
      .recover { case error => -\/(ApiError(s"Failed to add $username to reserved username list", error.getMessage)) }
}
