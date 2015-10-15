package controllers

import javax.inject.Inject

import models.{SearchResponse, UserSummary}
import play.api.mvc.{Action, Controller}
import repositories.UsersReadRepository
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class UsersController @Inject() (usersRepository: UsersReadRepository) extends Controller {

  def search(query: String) = Action.async { request =>
    usersRepository.findByEmail(query) map { results =>
      SearchResponse(results.map(UserSummary.fromUser))
    }
  }
}
