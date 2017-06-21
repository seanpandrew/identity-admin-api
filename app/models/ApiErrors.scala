package models

import play.api.libs.json._
import play.api.mvc._

import scala.language.implicitConversions

case class ApiError(message: String, details: String)

object ApiError {
  implicit val apiErrorWrites = new Writes[ApiError] {
    override def writes(o: ApiError): JsValue = Json.obj(
      "message" -> o.message,
      "details" -> o.details
    )
  }
}

//object ApiErrors {
//  def badRequest(msg: String): ApiError =
//    ApiError(
//      message = "Bad Request",
//      details = msg,
//      statusCode = 400
//    )
//
//  val notFound: ApiError =
//    ApiError(
//      message = "Not found",
//      details = "Not Found",
//      statusCode = 404
//    )
//
//  def internalError(msg: String): ApiError =
//    ApiError(
//      message = "Internal Server Error",
//      details = msg,
//      statusCode = 500
//    )
//
//  def unauthorized(msg: String) = ApiError(
//    message = "Unauthorized",
//    details = msg,
//    statusCode = 401
//  )
//}
