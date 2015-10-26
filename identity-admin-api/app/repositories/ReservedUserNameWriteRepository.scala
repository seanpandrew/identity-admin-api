package repositories

import javax.inject.Singleton

import com.gu.identity.model.ReservedUsernameList
import com.gu.identity.util.Logging
import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.SalatDAO
import models.{ApiErrors, ApiError}
import org.bson.types.ObjectId
import com.novus.salat.global._

import scala.util.{Failure, Success, Try}

case class ReservedUsername(_id: ObjectId, username: String)


@Singleton
class ReservedUserNameWriteRepository extends SalatDAO[ReservedUsername, ObjectId](collection=SalatMongoConnection.db()("reservedUsernames")) with Logging {

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
