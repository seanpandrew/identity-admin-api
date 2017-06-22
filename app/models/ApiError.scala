package models

import play.api.http.Writeable
import play.api.libs.json._
import scala.language.implicitConversions

case class ApiError(message: String, details: String = "")

object ApiError {
  implicit val format = Json.format[ApiError]

  /* Enables us to write, BadRequest(ApiError("some message")), vs. BadRequest(Json.toJson(ApiError("some message")))
     https://groups.google.com/forum/#!topic/play-framework/o93EEdg-fUA */
  implicit def jsWriteable[A](implicit wa: Writes[A], wjs: Writeable[JsValue]): Writeable[A] =
    wjs.map(a => Json.toJson(a))
}

