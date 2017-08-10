package repositories.sqlClients

import models.{ApiError, ApiResponse, User}
import repositories._

import scala.concurrent.Future
import scalaz.{-\/, \/-}

// TODO replace
import scala.concurrent.ExecutionContext.Implicits.global

trait UsersWriteRepo {
  def update(user: User, userUpdateRequest: IdentityUserUpdate): ApiResponse[User]
  def updateEmailValidationStatus(user: User, emailValidated: Boolean): ApiResponse[User]
  def delete(user: User): ApiResponse[Unit]
  def unsubscribeFromMarketingEmails(email: String): ApiResponse[User]
}

object NotFound extends Exception

object RepoHelpers {

  def asApiResponse[A](fut: Future[A]): ApiResponse[A] = {
    fut.map(\/-.apply).recover({case NotFound => -\/(ApiError("Not found"))})
  }

  def prepareUserForUpdate(userUpdateRequest: IdentityUserUpdate, identityUser: IdentityUser): IdentityUser = {
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
}