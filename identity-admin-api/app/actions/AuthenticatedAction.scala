package actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.google.inject.ImplementedBy
import com.gu.identity.util.Logging
import configuration.Config
import models.ApiErrors
import org.apache.commons.codec.binary.Base64
import play.api.http.HeaderNames
import play.api.libs.ws.WSRequest
import play.api.mvc.{Request, Result, _}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import play.api.mvc._

class AuthenticatedActionWithSecret extends AuthenticatedAction {
  val secret = Config.hmacSecret
}

@ImplementedBy(classOf[AuthenticatedActionWithSecret])
trait AuthenticatedAction extends ActionBuilder[Request] with Logging {

  def secret: String

  private val ALGORITHM = "HmacSHA256"
  private val HMAC_PATTERN = "HMAC\\s(.+)".r

  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    Try {
      val authorization = request.headers.get(HeaderNames.AUTHORIZATION).getOrElse(throw new IllegalArgumentException("Authorization header is required."))
      val hmac = extractToken(authorization).getOrElse(throw new IllegalArgumentException("Authorization header is invalid."))
      val date = request.headers.get(HeaderNames.DATE).getOrElse(throw new scala.IllegalArgumentException("Date header is required."))
      val uri = getPath(request)

      logger.info(s"path: $uri, date: $date, hmac: $hmac")

      val signed = sign(date, uri)

      if (signed == hmac) {
        Success
      } else {
        throw new IllegalArgumentException("Authorization token is invalid.")
      }
    } match {
      case Success(r) => block(request)
      case Failure(t) => Future.successful(ApiErrors.unauthorized(t.getMessage))
    }
  }

  private[actions] def getPath(request: Request[Any]): String = {
    val queryString = request.rawQueryString
    if(queryString == null || queryString.isEmpty) request.path else s"${request.path}?$queryString"
  }

  private[actions] def extractToken(authHeader: String): Option[String] = {
    val matched = HMAC_PATTERN.findAllIn(authHeader).matchData map {
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
    val signingKey = new SecretKeySpec(secret.getBytes, ALGORITHM)
    val mac = Mac.getInstance(ALGORITHM)
    mac.init(signingKey)
    val rawHmac = mac.doFinal(toEncode.getBytes)
    new String(Base64.encodeBase64(rawHmac))
  }
}
