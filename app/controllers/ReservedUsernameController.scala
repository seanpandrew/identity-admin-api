package controllers

import javax.inject.{Inject, Singleton}
import actions.AuthenticatedAction
import com.gu.identity.util.Logging
import models._
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.Controller
import repositories.ReservedUserNameWriteRepository
import scalaz.EitherT
import scalaz.std.scalaFuture._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

case class ReservedUsernameRequest(username: String)

object ReservedUsernameRequest {
  implicit val format = Json.format[ReservedUsernameRequest]
}

@Singleton
class ReservedUsernameController @Inject() (reservedUsernameRepository: ReservedUserNameWriteRepository, auth: AuthenticatedAction) extends Controller with Logging {

  def reserveUsername() = auth.async(parse.json) { request =>
    request.body.validate[ReservedUsernameRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Reserving username: ${result.username}")
        EitherT(reservedUsernameRepository.addReservedUsername(result.username)).fold(
          error => InternalServerError(error),
          _ => NoContent
        )
      case JsError(e) => Future.successful(BadRequest(ApiError("Failed to reserve username", e.toString)))
    }
  }

  def getReservedUsernames = auth.async { request =>
    EitherT(reservedUsernameRepository.loadReservedUsernames).fold(
      error => InternalServerError(error),
      success => Ok(Json.toJson(success))
    )
  }

  def unreserveUsername() = auth.async(parse.json) { request =>
    request.body.validate[ReservedUsernameRequest] match {
      case JsSuccess(result, path) =>
        logger.info(s"Unreserving username: ${result.username}")
        EitherT(reservedUsernameRepository.removeReservedUsername(result.username)).fold(
          error => InternalServerError(error),
          _ => NoContent
        )
      case JsError(error) => Future.successful(BadRequest(ApiError("Failed to unreserve username", error.toString)))
    }
  }

}
