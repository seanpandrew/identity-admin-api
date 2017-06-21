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
        reservedUsernameRepository.addReservedUsername(result.username) match {
          case Left(e) => InternalServerError(Json.toJson(e))
          case Right(success) => NoContent
        }
      case JsError(e) =>
//        ApiErrors.badRequest(e.toString())
        BadRequest(Json.toJson(ApiError("", e.toString())))
    }
  }

  def getReservedUsernames = auth { request =>
    reservedUsernameRepository.loadReservedUsernames match {
//      case Left(e) => e
      case Left(e) => InternalServerError(Json.toJson(e))
      case Right(success) => Ok(Json.toJson(success))
    }
  }

  def unreserveUsername() = auth(parse.json) { request =>
    request.body.validate[ReservedUsernameRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Unreserving username: ${result.username}")
        reservedUsernameRepository.removeReservedUsername(result.username) match {
//          case Left(e) => e
          case Left(e) => InternalServerError(Json.toJson(e))
          case Right(success) => NoContent
        }
      case JsError(e) =>
//        ApiErrors.badRequest(e.toString())
        BadRequest(Json.toJson(ApiError("", e.toString())))
    }
  }

}
