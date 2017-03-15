package services

import javax.inject.Inject

import actors.EventPublishingActor.{DisplayNameChanged, EmailValidationChanged}
import actors.EventPublishingActorProvider
import com.gu.identity.util.Logging
import models._
import repositories.{PersistedUserUpdate, ReservedUserNameWriteRepository, UsersReadRepository, UsersWriteRepository}
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import configuration.Config.PublishEvents.eventsEnabled

class UserService @Inject() (usersReadRepository: UsersReadRepository,
                             usersWriteRepository: UsersWriteRepository,
                             reservedUserNameRepository: ReservedUserNameWriteRepository,
                             identityApiClient: IdentityApiClient,
                             eventPublishingActorProvider: EventPublishingActorProvider) extends Logging {

  private lazy val UsernamePattern = "[a-zA-Z0-9]{6,20}".r

  def update(user: User, userUpdateRequest: UserUpdateRequest): ApiResponse[User] = {
    val emailValid = isEmailValid(user, userUpdateRequest)
    val usernameValid = isUsernameValid(user, userUpdateRequest)

    (emailValid, usernameValid) match {
      case (true, true) =>
        val userEmailChanged = !user.email.equalsIgnoreCase(userUpdateRequest.email)
        val userEmailValidated = if(userEmailChanged) Some(false) else user.status.userEmailValidated
        val userEmailValidatedChanged = isEmailValidationChanged(userEmailValidated, user.status.userEmailValidated)
        val usernameChanged = isUsernameChanged(userUpdateRequest.username, user.username)
        val displayNameChanged = isDisplayNameChanged(userUpdateRequest.displayName, user.displayName)
        val update = PersistedUserUpdate(userUpdateRequest, userEmailValidated)
        val result = usersWriteRepository.update(user, update)

        triggerEvents(
          userId = user.id,
          usernameChanged = usernameChanged,
          displayNameChanged = displayNameChanged,
          emailValidatedChanged = userEmailValidatedChanged
        )

        if(result.isRight && userEmailChanged)
          identityApiClient.sendEmailValidation(user.id)
        if (userEmailChanged && eventsEnabled) {
          SalesforceIntegration.enqueueUserUpdate(user.id, userUpdateRequest.email)
        }
        ApiResponse.Async(Future.successful(result))
      case (false, true) =>
        ApiResponse.Left(ApiErrors.badRequest("Email is invalid"))
      case (true, false) =>
        ApiResponse.Left(ApiErrors.badRequest("Username is invalid"))
      case _ =>
        ApiResponse.Left(ApiErrors.badRequest("Email and username are invalid"))
    }

  }

  def isDisplayNameChanged(newDisplayName: Option[String], existingDisplayName: Option[String]): Boolean = {
    newDisplayName != existingDisplayName
  }

  def isUsernameChanged(newUsername: Option[String], existingUsername: Option[String]): Boolean = {
    newUsername != existingUsername
  }

  def isEmailValidationChanged(newEmailValidated: Option[Boolean], existingEmailValidated: Option[Boolean]): Boolean = {
    newEmailValidated != existingEmailValidated
  }

  private def triggerEvents(userId: String, usernameChanged: Boolean, displayNameChanged: Boolean, emailValidatedChanged: Boolean) = {
    if (eventsEnabled) {
      if (usernameChanged || displayNameChanged) {
        eventPublishingActorProvider.sendEvent(DisplayNameChanged(userId))
      }
      if (emailValidatedChanged) {
        eventPublishingActorProvider.sendEvent(EmailValidationChanged(userId))
      }
    }
  }

  private def isUsernameValid(user: User, userUpdateRequest: UserUpdateRequest): Boolean = {
    def validateUsername(username: Option[String]): Boolean =  username match {
      case Some(newUsername) => UsernamePattern.pattern.matcher(newUsername).matches()
      case _ => true
    }

    user.username match {
      case None => validateUsername(userUpdateRequest.username)
      case Some(existing) => if(!existing.equalsIgnoreCase(userUpdateRequest.username.mkString)) validateUsername(userUpdateRequest.username) else true
    }
  }

  private def isEmailValid(user: User, userUpdateRequest: UserUpdateRequest): Boolean = {
    if (!user.email.equalsIgnoreCase(userUpdateRequest.email))
      EmailAddress.isValid(userUpdateRequest.email)
    else
      true
  }

  def search(query: String, limit: Option[Int] = None, offset: Option[Int] = None): ApiResponse[SearchResponse] =
    ApiResponse.Async.Right(usersReadRepository.search(query, limit, offset))

  def findById(id: String): ApiResponse[User] = {
    val result =  usersReadRepository.findById(id).map(_.toRight(ApiErrors.notFound))
    ApiResponse.Async(result)
  }

  def delete(userId: String, usernameToReserve: Option[String]): ApiResponse[ReservedUsernameList] = {
    val result = usersWriteRepository.delete(userId) match{
      case Right(r) =>
        usernameToReserve.map(username => reservedUserNameRepository.addReservedUsername(username)).getOrElse {
          reservedUserNameRepository.loadReservedUsernames
        }
      case Left(r) => Left(r)
    }
    ApiResponse.Async(Future.successful(result))
  }

  def validateEmail(user: User): ApiResponse[Boolean] = {
    val result = doValidateEmail(user, emailValidated = true)
    ApiResponse.Async(Future.successful(result))
  }

  private def doValidateEmail(user: User, emailValidated: Boolean): Either[ApiError, Boolean] = {
    usersWriteRepository.updateEmailValidationStatus(user, emailValidated) match{
      case Right(r) => {
        triggerEvents(
          userId = user.id,
          usernameChanged = false,
          displayNameChanged = false,
          emailValidatedChanged = true
        )
        Right(true)
      }
      case Left(r) => Left(r)
    }
  }

  def sendEmailValidation(user: User): ApiResponse[Boolean] = {
    val result = doValidateEmail(user, emailValidated = false) match {
      case Right(_) => identityApiClient.sendEmailValidation(user.id)
      case Left(r) => Future.successful(Left(r))
    }
    ApiResponse.Async(result)
  }

}
