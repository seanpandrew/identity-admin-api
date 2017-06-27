package repositories

import javax.inject.{Inject, Singleton}
import com.gu.identity.util.Logging
import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.MongoDBObject
import models.{ApiError, ReservedUsernameList}
import org.bson.types.ObjectId
import salat.dao.SalatDAO
import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}

case class ReservedUsername(_id: ObjectId, username: String)

@Singleton
class ReservedUserNameWriteRepository @Inject() (environment: play.api.Environment, salatMongoConnection: SalatMongoConnection)
  extends SalatDAO[ReservedUsername, ObjectId](collection=salatMongoConnection.db()("reservedUsernames")) with Logging {

  private def findReservedUsername(username: String): ApiError \/ ReservedUsername =
    Try(findOne(MongoDBObject("username" -> username))) match {
      case Success(Some(reservedUsername)) => \/-(reservedUsername)
      case Success(None) => -\/(ApiError("Username not found"))
      case Failure(error) =>
        val title = s"Failed to find reserved username $username"
        logger.error(title, error)
        -\/(ApiError(title, error.getMessage))
    }

  def removeReservedUsername(username: String): ApiError \/ ReservedUsernameList =
      findReservedUsername(username).fold(
        error => -\/(error),
        reservedUsername =>
          Try(remove(reservedUsername)) match {
            case Success(success) => loadReservedUsernames
            case Failure(error) =>
              val title = s"Failed to remove reserved username $username"
              logger.error(title, error)
              -\/(ApiError(title, error.getMessage))
          }
      )

  def loadReservedUsernames: ApiError \/ ReservedUsernameList =
    Try(cursorToReservedUsernameList(collection.find().sort(MongoDBObject("username" -> 1)))) match {
      case Success(reservedUsernameList) => \/-(reservedUsernameList)
      case Failure(error) =>
        val title = "Failed to load reserved usernames"
        logger.error(title, error)
        -\/(ApiError(title, error.getMessage))
    }

  private def cursorToReservedUsernameList(col: MongoCursor): ReservedUsernameList = ReservedUsernameList(col.map(dbObject => dbObject.get("username").asInstanceOf[String]).toList)

  def addReservedUsername(reservedUsername: String): ApiError \/ ReservedUsernameList =
    Try(insert(ReservedUsername(new ObjectId(), reservedUsername))) match {
      case Success(_) =>
        logger.info(s"Reserving username: $reservedUsername")
        loadReservedUsernames
      case Failure(error) =>
        val title = s"Failed to add $reservedUsername to reserved username list"
        logger.error(title, error)
        -\/(ApiError(title, error.getMessage))
    }
}
