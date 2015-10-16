package controllers

import javax.inject.Inject

import com.gu.identity.util.Logging
import models.{UserUpdateRequest, ApiErrors, SearchResponse}
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.{Action, Controller}
import repositories.UsersReadRepository
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

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

  def findById(id: String) = Action.async { request =>
    usersRepository.findById(id) map {
      case None => ApiErrors.notFound
      case Some(user) => user
    }
  }

  def update(id: String) = Action.async(parse.json) { request =>
    request.body.validate[UserUpdateRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Updating user id:$id, body: $result")
        usersRepository.findById(id) map {
          case None => ApiErrors.notFound
          case Some(user) =>
            // TODO validate update for fields and use write repo to perform it
            Ok
        }
      case JsError(e) => Future.successful(ApiErrors.badRequest(e.toString()))
    }

  }
}
