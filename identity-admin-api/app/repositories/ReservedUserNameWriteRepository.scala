package repositories

import javax.inject.Singleton

import com.gu.identity.util.Logging
import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.SalatDAO
import models.{ApiErrors, ApiError, ReservedUsernameList}
import org.bson.types.ObjectId
import com.novus.salat.global._

import scala.util.{Failure, Success, Try}

case class ReservedUsername(_id: ObjectId, username: String)


@Singleton
class ReservedUserNameWriteRepository extends SalatDAO[ReservedUsername, ObjectId](collection=SalatMongoConnection.db()("reservedUsernames")) with Logging {

  private def findReservedUsername(username: String): Either[ApiError, ReservedUsername] =
    Try {
      findOne(MongoDBObject("username" -> username))
    } match {
      case Success(Some(r)) => Right(r)
      case Success(None) => Left(ApiErrors.notFound)
      case Failure(t) =>
        logger.error(s"Could find reserved username: $username", t)
        Left(ApiErrors.internalError(t.getMessage))
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
            Left(ApiErrors.internalError(t.getMessage))
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
        Left(ApiErrors.internalError(t.getMessage))
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
        logger.error("Could not add to reserved username list", t)
        Left(ApiErrors.internalError(t.getMessage))
    }
  }

}
