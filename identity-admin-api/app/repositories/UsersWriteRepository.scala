package repositories

import javax.inject.Singleton

import com.gu.identity.util.Logging
import com.mongodb.casbah.WriteConcern
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
  
  def update(user: User, userUpdateRequest: PersistedUserUpdate): Either[ApiError, User] = {
    Try {
      findOne(MongoDBObject("_id" -> user.id)).map { persistedUser =>
        prepareUserForUpdate(userUpdateRequest, persistedUser)
      }
    } match {
      case Success(Some(userToSave)) =>
          doUpdate(userToSave)
      case Success(None) =>
        Left(ApiErrors.notFound)
       case Failure(t) =>
        logger.error(s"Failed to update user. id: ${user.id}", t)
        Left(ApiErrors.internalError(t.getMessage))
    }
  }

  def validateEmail(user: User): Either[ApiError, User] = {
    Try {
      findOne(MongoDBObject("_id" -> user.id)).map { persistedUser =>
        persistedUser.copy(statusFields = Some(persistedUser.statusFields.getOrElse(StatusFields().copy(userEmailValidated = Some(true)))))
      }
    } match {
      case Success(Some(userToSave)) =>
          doUpdate(userToSave)
      case Success(None) =>
        Left(ApiErrors.notFound)
       case Failure(t) =>
        logger.error(s"Failed to validate email for user id: ${user.id}", t)
        Left(ApiErrors.internalError(t.getMessage))
    }
  }

  private def prepareUserForUpdate(userUpdateRequest: PersistedUserUpdate, persistedUser: PersistedUser): PersistedUser = {
    val publicFields = persistedUser.publicFields.getOrElse(PublicFields()).copy(
      username = Some(userUpdateRequest.username),
      usernameLowerCase = Some(userUpdateRequest.username.toLowerCase),
      displayName = Some(userUpdateRequest.username),
      vanityUrl = Some(userUpdateRequest.username)
    )
    val privateFields = persistedUser.privateFields.getOrElse(PrivateFields()).copy(
      firstName = userUpdateRequest.firstName,
      secondName = userUpdateRequest.lastName
    )
    val statusFields = persistedUser.statusFields.getOrElse(StatusFields()).copy(
      receive3rdPartyMarketing = userUpdateRequest.receive3rdPartyMarketing,
      receiveGnmMarketing = userUpdateRequest.receiveGnmMarketing,
      userEmailValidated = userUpdateRequest.userEmailValidated
    )
    persistedUser.copy(
      primaryEmailAddress = userUpdateRequest.email,
      publicFields = Some(publicFields),
      privateFields = Some(privateFields),
      statusFields = Some(statusFields)
    )
  }

  private def doUpdate(userToSave: PersistedUser): Either[ApiError, User] = {
    Try {
      update(MongoDBObject("_id" -> userToSave._id), userToSave, upsert = false, multi = false, wc = WriteConcern.Safe)
    } match {
      case Success(_) =>
        Right(User.fromPersistedUser(userToSave))
      case Failure(t) =>
        logger.error(s"Failed to update user. id: ${userToSave._id}", t)
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
