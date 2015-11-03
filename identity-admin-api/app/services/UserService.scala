package services

import javax.inject.Inject
import com.gu.identity.model.ReservedUsernameList
import com.gu.identity.util.Logging
import models._
import repositories.{ReservedUserNameWriteRepository, UsersWriteRepository, UsersReadRepository}
import uk.gov.hmrc.emailaddress.EmailAddress
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserService @Inject() (usersReadRepository: UsersReadRepository,
                             usersWriteRepository: UsersWriteRepository,
                             reservedUserNameRepository: ReservedUserNameWriteRepository,
                             identityApiClient: IdentityApiClient) extends Logging {

  private lazy val UsernamePattern = "[a-zA-Z0-9]{6,20}".r

  def update(user: User, userUpdateRequest: UserUpdateRequest): ApiResponse[User] = {
    val emailValid = isEmailValid(user, userUpdateRequest)
    val usernameValid = isUsernameValid(user, userUpdateRequest)

    (emailValid, usernameValid) match {
      case (true, true) =>
        val result = usersWriteRepository.update(user, userUpdateRequest)
        ApiResponse.Async(Future.successful(result))
      case (false, true) =>
        ApiResponse.Left(ApiErrors.badRequest("Email is invalid"))
      case (true, false) =>
        ApiResponse.Left(ApiErrors.badRequest("Username is invalid"))
      case _ =>
        ApiResponse.Left(ApiErrors.badRequest("Email and username are invalid"))
    }

  }

  private def isUsernameValid(user: User, userUpdateRequest: UserUpdateRequest): Boolean = {
    def validateUsername(username: String) = UsernamePattern.pattern.matcher(username).matches()

    user.username match {
      case None => validateUsername(userUpdateRequest.username)
      case Some(existing) => if(!existing.equalsIgnoreCase(userUpdateRequest.username)) validateUsername(userUpdateRequest.username) else true
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

  def delete(user: User): ApiResponse[ReservedUsernameList] = {
    val result = usersWriteRepository.delete(user) match{
      case Right(r) => 
        user.username.map(username => reservedUserNameRepository.addReservedUsername(username)).getOrElse {
          reservedUserNameRepository.loadReservedUsernames
        }
      case Left(r) => Left(r)
    }
    ApiResponse.Async(Future.successful(result))
  }
  
  def sendEmailValidation(user: User): ApiResponse[Boolean] = {
    val result = identityApiClient.sendEmailValidation(user.id)
    ApiResponse.Async(result)
  }

}
