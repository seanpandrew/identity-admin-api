package repositories

import play.api.libs.json.{Format, JsPath, Reads, Writes}
import play.api.libs.functional.syntax._

case class DeletedUser(
  id: String,
  email: String,
  username: String)

object DeletedUser {
  val deletedUserReads: Reads[DeletedUser] =  (
    (JsPath \ "_id").read[String] and
    (JsPath \ "email").read[String] and
    (JsPath \ "username").read[String]
  )(DeletedUser.apply _)

  val deletedUserWrites: Writes[DeletedUser] = (
    (JsPath \ "_id").write[String] and
    (JsPath \ "email").write[String] and
    (JsPath \ "username").write[String]
  )(unlift(DeletedUser.unapply))

  implicit val format: Format[DeletedUser] =
    Format(deletedUserReads, deletedUserWrites)
}
