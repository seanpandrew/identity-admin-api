package models

import play.api.libs.json.Json

case class UserUpdateRequest(
                            email: String,
                            firstName: Option[String] = None,
                            lastName: Option[String] = None,
                            displayName: Option[String] = None,
                            username: Option[String] = None,
                            vanityUrl: Option[String] = None)

object UserUpdateRequest {
  implicit val format = Json.format[UserUpdateRequest]
}
