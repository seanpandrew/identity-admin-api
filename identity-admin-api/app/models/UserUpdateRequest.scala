package models

import play.api.libs.json.Json

case class UserUpdateRequest(
                            email: String,
                            username: String,
                            firstName: Option[String] = None,
                            lastName: Option[String] = None)

object UserUpdateRequest {
  implicit val format = Json.format[UserUpdateRequest]
}
