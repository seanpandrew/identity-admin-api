package actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.google.inject.ImplementedBy
import com.gu.identity.util.Logging
import configuration.Config
import models.ApiErrors
import org.apache.commons.codec.binary.Base64
import org.joda.time.{DateTimeZone, DateTime}
import play.api.http.HeaderNames
import play.api.mvc.{Request, Result, _}
import util.Formats

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import play.api.mvc._

class AuthenticatedActionWithSecret extends AuthenticatedAction {
  val secret = Config.hmacSecret
}

@ImplementedBy(classOf[AuthenticatedActionWithSecret])
trait AuthenticatedAction extends ActionBuilder[Request] with Logging {

  def secret: String

  private val Algorithm = "HmacSHA256"
  private val HmacPattern = "HMAC\\s(.+)".r
  private[actions] val HmacValidDurationInMinutes = 5
  private[actions] val MinuteInMilliseconds = 60000

  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    Try {
      val authorization = request.headers.get(HeaderNames.AUTHORIZATION).getOrElse(throw new IllegalArgumentException("Authorization header is required."))
      val hmac = extractToken(authorization).getOrElse(throw new IllegalArgumentException("Authorization header is invalid."))
      val date = request.headers.get(HeaderNames.DATE).getOrElse(throw new scala.IllegalArgumentException("Date header is required."))
      val uri = request.uri

      logger.info(s"path: $uri, date: $date, hmac: $hmac")

      if (isDateValid(date) && isHmacValid(date, uri, hmac)) {
        Success
      } else {
        throw new IllegalArgumentException("Authorization token is invalid.")
      }
    } match {
      case Success(r) => block(request)
      case Failure(t) => Future.successful(ApiErrors.unauthorized(t.getMessage))
    }
  }

  private def isDateValid(date: String): Boolean  = {
    Try {
      val provided = Formats.toDateTime(date)
      val now = DateTime.now(DateTimeZone.forID("GMT"))
      val delta = Math.abs(provided.getMillis - now.getMillis)
      val allowedOffset = HmacValidDurationInMinutes * MinuteInMilliseconds
      delta <= allowedOffset
    } match {
      case Success(r) => r
      case Failure(t) =>
        logger.error(s"Date header could not be parsed", t)
        false
    }
  }

  private def isHmacValid(date: String, uri: String, hmac: String): Boolean =
    sign(date, uri) == hmac

  private[actions] def extractToken(authHeader: String): Option[String] = {
    val matched = HmacPattern.findAllIn(authHeader).matchData map {
      m => m.group(1)
    }

    matched.toSeq.headOption
  }

  def sign(date: String, path: String): String = {
    val input = List[String](date, path)
    val toSign = input.mkString("\n")
    calculateHMAC(toSign)
  }


  private def calculateHMAC(toEncode: String): String = {
    val signingKey = new SecretKeySpec(secret.getBytes, Algorithm)
    val mac = Mac.getInstance(Algorithm)
    mac.init(signingKey)
    val rawHmac = mac.doFinal(toEncode.getBytes)
    new String(Base64.encodeBase64(rawHmac))
  }
}
