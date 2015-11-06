package models

import play.api.libs.json.Json

case class ReservedUsernameList(reservedUsernames: List[String] = Nil)

object ReservedUsernameList {
  implicit val format = Json.format[ReservedUsernameList]
}
