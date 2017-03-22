package models

import org.joda.time.DateTime
import play.api.libs.json.Json
import repositories.PersistedUser
import scala.language.implicitConversions
import MongoJsFormats._

case class UserSummary(id: String,
                       email: String,
                       username: Option[String],
                       firstName: Option[String],
                       lastName: Option[String],
                       creationDate: Option[DateTime],
                       lastActivityDate: Option[DateTime],
                       registrationIp: Option[String],
                       lastActiveIpAddress: Option[String])



object UserSummary {
  implicit val format = Json.format[UserSummary]

  def fromUser(user: PersistedUser): UserSummary =
    UserSummary(
      id = user._id.getOrElse(""),
      email = user.primaryEmailAddress,
      username = user.publicFields.flatMap(_.username),
      firstName = user.privateFields.flatMap(_.firstName),
      lastName = user.privateFields.flatMap(_.secondName),
      creationDate = user.dates.flatMap(_.accountCreatedDate),
      lastActivityDate = user.dates.flatMap(_.lastActivityDate),
      registrationIp = user.privateFields.flatMap(_.registrationIp),
      lastActiveIpAddress= user.privateFields.flatMap(_.lastActiveIpAddress)
    )

}
