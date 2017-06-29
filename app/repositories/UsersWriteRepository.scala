package repositories

import javax.inject.{Inject, Singleton}
import com.gu.identity.util.Logging
import models._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection._
import reactivemongo.play.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import scalaz.{-\/, EitherT, OptionT, \/-}
import scalaz.std.scalaFuture._

@Singleton class UsersWriteRepository @Inject() (
    reactiveMongoApi: ReactiveMongoApi,
    deletedUsersRepository: DeletedUsersRepository) extends Logging {

  private lazy val usersF = reactiveMongoApi.database.map(_.collection("users"))

  def findBy(query: String): ApiResponse[IdentityUser] =
    OptionT(usersF.flatMap(_.find(buildSearchQuery(query)).one[IdentityUser])).fold(
      user => \/-(user),
      -\/(ApiError("User not found"))
    )

  private def buildSearchQuery(query: String) =
    Json.obj(
      "$or" -> Json.arr(
        Json.obj("_id" -> query.toLowerCase),
        Json.obj("primaryEmailAddress" -> query.toLowerCase)
      )
    )

  def update(user: User, userUpdateRequest: IdentityUserUpdate): ApiResponse[User] =
    (for {
      persistedUser <- EitherT(findBy(user.id))
      updatedUser <- EitherT(doUpdate(prepareUserForUpdate(userUpdateRequest, persistedUser)))
    } yield (updatedUser)).run

  def updateEmailValidationStatus(user: User, emailValidated: Boolean): ApiResponse[User] =
    (for {
      persistedUser <- EitherT(findBy(user.id))
      statusFields = persistedUser.statusFields.getOrElse(StatusFields()).copy(userEmailValidated = Some(emailValidated))
      updatedUser <- EitherT(doUpdate(persistedUser.copy(statusFields = Some(statusFields))))
    } yield (updatedUser)).run

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

  private def doUpdate(userToSave: IdentityUser): ApiResponse[User] =
    usersF.flatMap(_.update(buildSearchQuery(userToSave._id.get), userToSave)).map( _ => \/-(User.fromIdentityUser(userToSave)))
      .recover { case error => -\/(ApiError("Failed to update user", error.getMessage))}

  def delete(user: User): ApiResponse[Boolean] =
    usersF.flatMap(_.remove(buildSearchQuery(user.id))).map(_ => \/-(true))
      .recover { case error => -\/(ApiError("Failed to delete user", error.getMessage)) }

  def unsubscribeFromMarketingEmails(email: String) =
    (for {
      persistedUser <- EitherT(findBy(email))
      statusFields = persistedUser.statusFields.getOrElse(StatusFields()).copy(receive3rdPartyMarketing = Some(false), receiveGnmMarketing = Some(false))
      updatedUser <- EitherT(doUpdate(persistedUser.copy(statusFields = Some(statusFields))))
    } yield (updatedUser)).run
}
