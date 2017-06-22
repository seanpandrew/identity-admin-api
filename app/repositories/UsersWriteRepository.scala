package repositories

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import models._
import salat.dao.SalatDAO

import scala.util.{Failure, Success, Try}

@Singleton
class UsersWriteRepository @Inject() (
    salatMongoConnection: SalatMongoConnection,
    deletedUsersRepository: DeletedUsersRepository)
  extends SalatDAO[IdentityUser, String](collection=salatMongoConnection.db()("users")) with Logging {

  private[repositories] def createUser(user: IdentityUser) = {
    val userToCreate = user.copy(
      primaryEmailAddress = user.primaryEmailAddress.toLowerCase,
      publicFields = user.publicFields.map(pf => pf.copy(usernameLowerCase = pf.username.map(_.toLowerCase))))
    insert(userToCreate)
  }
  
  def update(user: User, userUpdateRequest: IdentityUserUpdate): Either[ApiError, User] = {
    Try {
      findOne(MongoDBObject("_id" -> user.id)).map { persistedUser =>
        prepareUserForUpdate(userUpdateRequest, persistedUser)
      }
    } match {
        case Success(Some(userToSave)) => doUpdate(userToSave)
        case Success(None) => Left(ApiError("User not found"))
        case Failure(error) =>
          val title = s"Failed to update user ${user.id}"
          logger.error(title, error)
          Left(ApiError(title, error.getMessage))
    }
  }

  def updateEmailValidationStatus(user: User, emailValidated: Boolean): Either[ApiError, User] = {
    Try {
      findOne(MongoDBObject("_id" -> user.id)).map { persistedUser =>
        val statusFields = persistedUser.statusFields.getOrElse(StatusFields()).copy(
          userEmailValidated = Some(emailValidated)
        )
        persistedUser.copy(statusFields = Some(statusFields))
      }
    } match {
        case Success(Some(userToSave)) => doUpdate(userToSave)
        case Success(None) => Left(ApiError("User not found"))
        case Failure(error) =>
          val title = s"Failed to update email validation status to $emailValidated for user ${user.id}"
          logger.error(title, error)
          Left(ApiError(title, error.getMessage))
    }
  }

  private def prepareUserForUpdate(userUpdateRequest: IdentityUserUpdate, identityUser: IdentityUser): IdentityUser = {
    val publicFields = identityUser.publicFields.getOrElse(PublicFields()).copy(
      username = userUpdateRequest.username,
      usernameLowerCase = userUpdateRequest.username.map(_.toLowerCase),
      displayName = userUpdateRequest.displayName,
      vanityUrl = userUpdateRequest.username,
      location = userUpdateRequest.location,
      aboutMe = userUpdateRequest.aboutMe,
      interests = userUpdateRequest.interests
    )
    val privateFields = identityUser.privateFields.getOrElse(PrivateFields()).copy(
      firstName = userUpdateRequest.firstName,
      secondName = userUpdateRequest.lastName
    )
    val statusFields = identityUser.statusFields.getOrElse(StatusFields()).copy(
      receive3rdPartyMarketing = userUpdateRequest.receive3rdPartyMarketing,
      receiveGnmMarketing = userUpdateRequest.receiveGnmMarketing,
      userEmailValidated = userUpdateRequest.userEmailValidated
    )
    val searchFields = identityUser.searchFields.getOrElse(SearchFields()).copy(
      emailAddress = Some(userUpdateRequest.email.toLowerCase),
      username = userUpdateRequest.username.map(_.toLowerCase),
      displayName = userUpdateRequest.displayName
    )
    identityUser.copy(
      primaryEmailAddress = userUpdateRequest.email.toLowerCase,
      publicFields = Some(publicFields),
      privateFields = Some(privateFields),
      statusFields = Some(statusFields),
      searchFields = Some(searchFields)
    )
  }

  private def doUpdate(userToSave: IdentityUser): Either[ApiError, User] = {
    Try {
      update(MongoDBObject("_id" -> userToSave._id), userToSave, upsert = false, multi = false, wc = WriteConcern.Safe)
    } match {
      case Success(_) => Right(User.fromIdentityUser(userToSave))
      case Failure(error) =>
        val title = s"Failed to update user ${userToSave._id}"
        logger.error(title, error)
        Left(ApiError(title, generateErrorMessage(error)))
    }
  }

  private def generateErrorMessage(error: Throwable): String = {
    val errorText = error.toString
    if (errorText contains "E11000 duplicate key error")
      "this data is already in use in the database"
    else
      "update could not be performed contact identitydev@guardian.co.uk"
  }

  def delete(user: User): Either[ApiError, Boolean] = {
    Try {
      removeById(user.id)
    } match {
      case Success(r) =>
        deletedUsersRepository.insert(user.id, user.email, user.username.getOrElse(""))
        Right(true)
      case Failure(error) =>
        val title = s"Failed to delete user ${user.id}"
        logger.error(title, error)
        Left(ApiError(title, error.getMessage))
    }
  }

  def unsubscribeFromMarketingEmails(email: String) = {
    Try {
      findOne(MongoDBObject("primaryEmailAddress" -> email)).map { persistedUser =>
        val statusFields = persistedUser.statusFields.getOrElse(StatusFields()).copy(
          receive3rdPartyMarketing = Some(false),
          receiveGnmMarketing = Some(false)
        )
        persistedUser.copy(statusFields = Some(statusFields))
      }
    } match {
      case Success(Some(userToSave)) => doUpdate(userToSave)
      case Success(None) => Left(ApiError("User not found"))
      case Failure(error) =>
        val title = "Failed to unsubscribe from marketing emails:"
        logger.error(title, error)
        Left(ApiError(title, error.getMessage))
    }
  }
}
