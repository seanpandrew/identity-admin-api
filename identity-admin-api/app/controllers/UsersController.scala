package controllers

import javax.inject.Inject

import models.{ApiErrors, SearchResponse, UserSummary}
import play.api.mvc.{Action, Controller}
import repositories.UsersReadRepository
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class UsersController @Inject() (usersRepository: UsersReadRepository) extends Controller {

  private val MinimumQueryLength = 3

  def search(query: String) = Action.async { request =>
    if (query.length < MinimumQueryLength) {
      Future.successful(ApiErrors.badRequest(s"The query string must be a minimum of $MinimumQueryLength characters"))
    } else {
      usersRepository.findByEmail(query) map { results =>
        SearchResponse(results.map(UserSummary.fromUser))
      }
    }
  }
}
