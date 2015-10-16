package controllers

import javax.inject.Inject

import models.{ApiErrors, SearchResponse}
import play.api.mvc.{Action, Controller}
import repositories.UsersReadRepository
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class UsersController @Inject() (usersRepository: UsersReadRepository) extends Controller {

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
}
