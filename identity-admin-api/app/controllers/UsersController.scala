package controllers

import javax.inject.Inject
import actions.AuthenticatedAction
import com.gu.identity.util.Logging
import models._
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.utils.UriEncoding
import services.UserService

import scala.concurrent.Future

class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)

class UsersController @Inject() (userService: UserService, auth: AuthenticatedAction) extends Controller with Logging {

  private val MinimumQueryLength = 3

  def search(query: String, limit: Option[Int], offset: Option[Int]) = auth.async { request =>
    ApiResponse {
      if (offset.getOrElse(0) < 0) {
        ApiResponse.Left(ApiErrors.badRequest("offset must be a positive integer"))
      }
      else if (limit.getOrElse(0) < 0) {
        ApiResponse.Left(ApiErrors.badRequest("limit must be a positive integer"))
      }
      else if (query.length < MinimumQueryLength) {
        ApiResponse.Left(ApiErrors.badRequest(s"query must be a minimum of $MinimumQueryLength characters"))
      }
      else {
        val formattedQuery = query.replace(" ", "%20")
        userService.search(UriEncoding.decodePathSegment(formattedQuery, "UTF-8"), limit, offset)
      }
    }
  }

  private def UserAction(userId: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {
      userService.findById(userId).map(user => new UserRequest(user, input)).asFuture.map {
        case Left(r) => Left(ApiError.apiErrorToResult(r))
        case Right(r) => Right(r)
      }
    }
  }

  def findById(id: String) = (auth andThen UserAction(id)) { request =>
    request.user
  }

  def update(id: String) = (auth andThen UserAction(id)).async(parse.json) { request =>
    ApiResponse {
      request.body.validate[UserUpdateRequest] match {
        case JsSuccess(result, path) =>
          logger.info(s"Updating user id:$id, body: $result")
          userService.update(request.user, result)
        case JsError(e) => 
          ApiResponse.Left(ApiErrors.badRequest(e.toString()))
      }
    }
  }

  def delete(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Deleting user with id: $id")
    userService.delete(request.user).asFuture.map {
      case Right(r) => NoContent
      case Left(r) => ApiError.apiErrorToResult(r)
    }
  }
  
  def sendEmailValidation(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Sending email validation for user with id: $id")
    userService.sendEmailValidation(request.user).asFuture.map {
      case Right(r) => NoContent
      case Left(r) => ApiError.apiErrorToResult(r)
    }
  }

  def validateEmail(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Validating email for user with id: $id")
    userService.validateEmail(request.user).asFuture.map {
      case Right(r) => NoContent
      case Left(r) => ApiError.apiErrorToResult(r)
    }
  }
}
