package controllers

import javax.inject.{Inject, Singleton}
import actions.AuthenticatedAction
import com.gu.identity.util.Logging
import models._
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.Controller
import repositories.ReservedUserNameWriteRepository

case class ReservedUsernameRequest(username: String)

object ReservedUsernameRequest {
  implicit val format = Json.format[ReservedUsernameRequest]
}

@Singleton
class ReservedUsernameController @Inject() (reservedUsernameRepository: ReservedUserNameWriteRepository, auth: AuthenticatedAction) extends Controller with Logging {

  def reserveUsername() = auth(parse.json) { request =>
    request.body.validate[ReservedUsernameRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Reserving username: ${result.username}")
        reservedUsernameRepository.addReservedUsername(result.username).fold(
          error => InternalServerError(error),
          _ => NoContent
        )
      case JsError(e) => BadRequest(ApiError("Failed to reserve username", e.toString))
    }
  }

  def getReservedUsernames = auth { request =>
    reservedUsernameRepository.loadReservedUsernames.fold(
      error => InternalServerError(error),
      success => Ok(Json.toJson(success))
    )
  }

  def unreserveUsername() = auth(parse.json) { request =>
    request.body.validate[ReservedUsernameRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Unreserving username: ${result.username}")
        reservedUsernameRepository.removeReservedUsername(result.username).fold(
          error => InternalServerError(error),
          _ => NoContent
        )
      case JsError(error) => BadRequest(ApiError("Failed to unreserve username", error.toString))
    }
  }

}
