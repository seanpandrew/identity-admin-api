package repositories

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.api.libs.functional.syntax._

trait PostgresJsonFormats {

  implicit lazy val objectMapFormat = MongoJsonFormats.objectMapFormat

  implicit lazy val dateTimeRead: Reads[DateTime] = new Reads[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] = json match {
      case JsNumber(v) => JsSuccess(new DateTime(v.toLongExact, DateTimeZone.UTC))
      case other => JsError(s"Expected a number, got $other")
    }
  }

  implicit lazy val dateTimeWrite: Writes[DateTime] = new Writes[DateTime] {
    def writes(dateTime: DateTime): JsValue = JsNumber(dateTime.getMillis)
  }

  implicit lazy val userDatesFormat = Json.format[UserDates]
  implicit lazy val groupMemberShipFormat = Json.format[GroupMembership]


  private lazy val identityUserReads: Reads[IdentityUser] = (
    (JsPath \ "primaryEmailAddress").read[String] and
      (JsPath \ "_id").read[String] and
      (JsPath \ "publicFields").readNullable[PublicFields] and
      (JsPath \ "privateFields").readNullable[PrivateFields] and
      (JsPath \ "statusFields").readNullable[StatusFields] and
      (JsPath \ "dates").readNullable[UserDates] and
      (JsPath \ "password").readNullable[String] and
      (JsPath \ "userGroups").readNullable[List[GroupMembership]].map(_.getOrElse(Nil)) and
      (JsPath \ "socialLinks").readNullable[List[SocialLink]].map(_.getOrElse(Nil)) and
      (JsPath \ "adData").readNullable[Map[String, Any]].map(_.getOrElse(Map.empty)) and
      (JsPath \ "searchFields").readNullable[SearchFields])(IdentityUser.apply _)

  private lazy val identityUserWrites: OWrites[IdentityUser] = (
    (JsPath \ "primaryEmailAddress").write[String] and
      (JsPath \ "_id").write[String] and
      (JsPath \ "publicFields").writeNullable[PublicFields] and
      (JsPath \ "privateFields").writeNullable[PrivateFields] and
      (JsPath \ "statusFields").writeNullable[StatusFields] and
      (JsPath \ "dates").writeNullable[UserDates] and
      (JsPath \ "password").writeNullable[String] and
      (JsPath \ "userGroups").write[List[GroupMembership]] and
      (JsPath \ "socialLinks").write[List[SocialLink]] and
      (JsPath \ "adData").write[Map[String, Any]] and
      (JsPath \ "searchFields").writeNullable[SearchFields]
    )(unlift(IdentityUser.unapply))

  implicit lazy val format: OFormat[IdentityUser] = OFormat(identityUserReads, identityUserWrites)
}
