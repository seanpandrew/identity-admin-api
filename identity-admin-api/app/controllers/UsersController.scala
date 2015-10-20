package controllers

import javax.inject.Inject
import com.gu.identity.util.Logging
import models.{UserResponse, UserUpdateRequest, ApiErrors, SearchResponse}
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import repositories.UsersReadRepository
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class UserRequest[A](val user: UserResponse, request: Request[A]) extends WrappedRequest[A](request)

class UsersController @Inject() (usersRepository: UsersReadRepository) extends Controller with Logging {

  private val MinimumQueryLength = 3

  def search(query: String, limit: Option[Int], offset: Option[Int]) = Action.async { request =>
    if(offset.getOrElse(0) < 0) {
      Future.successful(ApiErrors.badRequest("offset must be a positive integer"))
    }
    else if(limit.getOrElse(0) < 0) {
      Future.successful(ApiErrors.badRequest("limit must be a positive integer"))
    }
    else if (query.length < MinimumQueryLength) {
      Future.successful(ApiErrors.badRequest(s"query must be a minimum of $MinimumQueryLength characters"))
    }
    else {
      usersRepository.search(query, limit, offset).map(SearchResponse.searchResponseToResult)
    }
  }

  def UserAction(userId: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] =
      usersRepository.findById(userId) map {
        case Some(user) => Right(new UserRequest(user, input))
        case None => Left(ApiErrors.notFound)
      }
  }

  def findById(id: String) = (Action andThen UserAction(id)) { request =>
    request.user
  }

  def update(id: String) = (Action andThen UserAction(id))(parse.json) { request =>
    request.body.validate[UserUpdateRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Updating user id:$id, body: $result")
        NoContent
      case JsError(e) => ApiErrors.badRequest(e.toString())
    }
  }

  def delete(id: String) = (Action andThen UserAction(id)) { request =>
    logger.info(s"Deleting user with id: $id")
    NoContent
  }
}
