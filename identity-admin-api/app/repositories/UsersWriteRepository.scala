package repositories

import javax.inject.Singleton

import com.gu.identity.util.Logging
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.SalatDAO
import com.novus.salat.global._
import models._

import scala.util.{Failure, Success, Try}

@Singleton
class UsersWriteRepository extends SalatDAO[PersistedUser, String](collection=SalatMongoConnection.db()("users")) with Logging {

  def createUser(user: PersistedUser) = {
    insert(user)
  }
  
  def update(user: User, userUpdateRequest: UserUpdateRequest): Either[ApiError, User] = {
    Try {
      update(MongoDBObject("_id" -> user.id),  MongoDBObject("primaryEmailAddress" -> userUpdateRequest.email), upsert = false, multi = false)
    } match {
      case Success(r) =>
        val updated = user.copy(email = userUpdateRequest.email)
        Right(updated)
      case Failure(t) =>
        logger.error(s"Failed to update user. id: ${user.id}, updateRequest: $userUpdateRequest", t)
        Left(ApiErrors.internalError(t.getMessage))
    }
  }

  def delete(user: User): Either[ApiError, Boolean] = {
    Try {
      removeById(user.id)
    } match {
      case Success(r) =>
        Right(true)
      case Failure(t) =>
        logger.error(s"Failed to delete user. id: ${user.id}", t)
        Left(ApiErrors.internalError(t.getMessage))
    }
  }
}
