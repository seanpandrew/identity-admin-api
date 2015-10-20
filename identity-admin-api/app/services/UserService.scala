package services

import javax.inject.Inject

import com.gu.identity.util.Logging
import models._
import repositories.{UsersWriteRepository, UsersReadRepository}
import scala.concurrent.ExecutionContext.Implicits.global

class UserService @Inject() (usersReadRepository: UsersReadRepository, usersWriteRepository: UsersWriteRepository) extends Logging {

  def handleError[T]: PartialFunction[Throwable, Either[ApiError, T]] = {
    case t: Throwable =>
      logger.error("Unexpected error", t)
      scala.Left(ApiErrors.internalError(t.getMessage))
  }
  
  def update(user: User, userUpdateRequest: UserUpdateRequest): ApiResponse[User] = {
    if(!user.email.equalsIgnoreCase(userUpdateRequest.email)) {
      val updateResult = usersReadRepository.findByEmail(userUpdateRequest.email) map { x => usersWriteRepository.update(user, userUpdateRequest)}
      ApiResponse.Async(updateResult, handleError)
    } else {
      ApiResponse.Right(user)
    }
  }

  def search(query: String, limit: Option[Int] = None, offset: Option[Int] = None): ApiResponse[SearchResponse] =
    ApiResponse.Async.Right(usersReadRepository.search(query, limit, offset))

  def findById(id: String): ApiResponse[User] = {
    val result =  usersReadRepository.findById(id).map(_.toRight(ApiErrors.notFound))
    ApiResponse.Async(result, handleError)
  }

}
