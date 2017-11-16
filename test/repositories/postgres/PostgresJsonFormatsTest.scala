package repositories.postgres

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.json.{JsString, JsSuccess}

class PostgresJsonFormatsTest extends WordSpecLike
  with Matchers {

  trait TestFixture extends PostgresJsonFormats

  "Postgres Json Formats" should {

    "Support reading ISO-8601 date time strings produced by python's datetime#isoformat()" in new TestFixture {
      // Python does not put the `Z` for zulu time so produces invalid ISO-8601 strings.
      // https://stackoverflow.com/a/23705687/2823715
      dateTimeRead.reads(JsString("2017-11-16T09:54:21")) shouldBe JsSuccess(DateTime.parse("2017-11-16T09:54:21Z"))
      dateTimeRead.reads(JsString("2017-11-16T09:54:21Z")) shouldBe JsSuccess(DateTime.parse("2017-11-16T09:54:21Z"))
    }

    "Write dates adhering to the ISO Spec" in new TestFixture {
      dateTimeWrite.writes(new DateTime(42000l, DateTimeZone.UTC)) shouldBe JsString("1970-01-01T00:00:42Z")
    }

    "Read written dates" in new TestFixture {
      val expectedDate = new DateTime(42000l, DateTimeZone.UTC)
      dateTimeRead.reads(dateTimeWrite.writes(expectedDate)) shouldBe JsSuccess(expectedDate)
    }
  }
}
