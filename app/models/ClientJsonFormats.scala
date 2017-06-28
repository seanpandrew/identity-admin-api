package models

import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import play.api.libs.json._

object ClientJsonFormats { // Format for identity-admin client of the API
  private val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC()
  implicit val dateTimeWrite: Writes[DateTime] = new Writes[DateTime] {
    def writes(dateTime: DateTime): JsValue = JsString(dateFormat.print(dateTime))
  }
}
