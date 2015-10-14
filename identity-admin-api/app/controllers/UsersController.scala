package controllers

import javax.inject.Inject

import models.ApiErrors
import play.api.mvc.{Action, Controller}
import repositories.UsersRepository
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class UsersController @Inject() (usersRepository: UsersRepository) extends Controller {

  def findUserByEmail(email: String) = Action.async { request =>
    usersRepository.findByEmail(email) map {
      case None => ApiErrors.notFound
      case Some(user) => user
    }
  }
}
