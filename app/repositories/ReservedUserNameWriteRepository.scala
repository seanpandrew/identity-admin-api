package repositories

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.MongoDBObject
import models.{ApiError, ReservedUsernameList}
import org.bson.types.ObjectId
import salat.dao.SalatDAO

import scala.util.{Failure, Success, Try}

case class ReservedUsername(_id: ObjectId, username: String)


@Singleton
class ReservedUserNameWriteRepository @Inject() (environment: play.api.Environment, salatMongoConnection: SalatMongoConnection)
  extends SalatDAO[ReservedUsername, ObjectId](collection=salatMongoConnection.db()("reservedUsernames")) with Logging {

  private def findReservedUsername(username: String): Either[ApiError, ReservedUsername] =
    Try {
      findOne(MongoDBObject("username" -> username))
    } match {
      case Success(Some(r)) => Right(r)
      case Success(None) => Left(ApiError("", ""))
      case Failure(t) =>
        logger.error(s"Could find reserved username: $username", t)
        Left(ApiError("", t.getMessage))
    }

  def removeReservedUsername(username: String): Either[ApiError, ReservedUsernameList] =
      findReservedUsername(username) match {
        case Right(r) => Try {
          remove(r)
        } match {
          case Success(success) =>
            loadReservedUsernames
          case Failure(t) =>
            logger.error(s"Could remove reserved username: $username", t)
            Left(ApiError("", t.getMessage))
        }
        case Left(l) => Left(l)
      }

  def loadReservedUsernames: Either[ApiError, ReservedUsernameList] =
    Try {
      cursorToReservedUsernameList(collection.find().sort(MongoDBObject("username" -> 1)))
    } match {
      case Success(r) => Right(r)
      case Failure(t) =>
        logger.error("Could not load reserved usernames", t)
        Left(ApiError("", t.getMessage))
    }

  private def cursorToReservedUsernameList(col: MongoCursor): ReservedUsernameList = ReservedUsernameList(col.map(dbObject => dbObject.get("username").asInstanceOf[String]).toList)

  def addReservedUsername(reservedUsername: String): Either[ApiError, ReservedUsernameList]  = {
    Try {
      insert(ReservedUsername(new ObjectId(), reservedUsername))
    } match {
      case Success(r) =>
        logger.info(s"Reserving username: $reservedUsername")
        loadReservedUsernames
      case Failure(t) =>
        val title = s"Could not add $reservedUsername to reserved username list"
        logger.error(title, t)
        Left(ApiError(title, t.getMessage))
    }
  }

}
