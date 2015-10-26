package services

import javax.inject.Inject

import com.gu.identity.model.ReservedUsernameList
import com.gu.identity.util.Logging
import models._
import repositories.{ReservedUserNameWriteRepository, UsersWriteRepository, UsersReadRepository}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserService @Inject() (usersReadRepository: UsersReadRepository,
                             usersWriteRepository: UsersWriteRepository,
                             reservedUserNameRepository: ReservedUserNameWriteRepository) extends Logging {

  def update(user: User, userUpdateRequest: UserUpdateRequest): ApiResponse[User] = {
    if(!user.email.equalsIgnoreCase(userUpdateRequest.email)) {
      val updateResult = usersReadRepository.findByEmail(userUpdateRequest.email) map {
        case None => usersWriteRepository.update(user, userUpdateRequest)
        case Some(existing) => Left(ApiErrors.badRequest("Email is in use"))
      }
      ApiResponse.Async(updateResult)
    } else {
      ApiResponse.Right(user)
    }
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

}
