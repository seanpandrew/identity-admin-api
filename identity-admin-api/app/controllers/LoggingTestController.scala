package controllers

import models.ApiErrors
import play.api.mvc.{Action, Results}

class LoggingTestController extends Results {

  def badRequest() = Action { ApiErrors.badRequest("invalid") }
  def internalError() = Action { ApiErrors.internalError("error") }
  def unauthorized() = Action { ApiErrors.unauthorized }
  
}
