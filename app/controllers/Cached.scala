package controllers

import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import play.api.mvc.Result
import util.Formats

import scala.math.max

object Cached {

  private val cacheableStatusCodes = Seq(200, 301, 404)

  private val tenDaysInSeconds = 10.days.standardDuration.seconds

  def apply(result: Result): Result = apply(60)(result)

  def apply(seconds: Int)(result: Result): Result = {
    if (suitableForCaching(result)) cacheHeaders(seconds, result) else result
  }

  def suitableForCaching(result: Result): Boolean = cacheableStatusCodes.contains(result.header.status)

  private def cacheHeaders(maxAge: Int, result: Result) = {
    val now = DateTime.now
    val staleWhileRevalidateSeconds = max(maxAge / 10, 1)
    result.withHeaders(
      "Cache-Control" -> s"public, max-age=$maxAge, stale-while-revalidate=$staleWhileRevalidateSeconds, stale-if-error=$tenDaysInSeconds",
      "Expires" -> Formats.toHttpDateTimeString(now + maxAge.seconds),
      "Date" -> Formats.toHttpDateTimeString(now)
    )
  }
}

object NoCache {
  def apply(result: Result): Result = result.withHeaders("Cache-Control" -> "no-cache, private", "Pragma" -> "no-cache")
}
