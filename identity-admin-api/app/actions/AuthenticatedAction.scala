package actions

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.gu.identity.util.Logging
import models.ApiErrors
import org.apache.commons.codec.binary.Base64
import play.api.http.HeaderNames
import play.api.mvc.{Request, Result, _}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import play.api.mvc._

object AuthenticatedAction extends ActionBuilder[Request] with Logging {

  val ALGORITHM = "HmacSHA256"
  private val HMAC_PATTERN = "HMAC\\s(.+)".r

  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    Try {
      val authorization = request.headers.get(HeaderNames.AUTHORIZATION).getOrElse(throw new IllegalArgumentException("Authorization header is required."))
      val hmac = extractToken(authorization)
      val date = request.headers.get(HeaderNames.DATE).getOrElse(throw new scala.IllegalArgumentException("Date header is required."))
      val uri = request.uri

      val signed = sign(date, uri)

      if (signed == hmac) {
        Success
      } else {
        throw new IllegalArgumentException("Authorization token is invalid")
      }
    } match {
      case Success(r) =>
        block(request)
      case Failure(t) =>
        logger.info(s"Authentication failed due to ${t.getMessage}")
        Future.successful(ApiErrors.unauthorized)
    }
  }

  private def extractToken(authHeader: String): String = {
    val matched = HMAC_PATTERN.findAllIn(authHeader).matchData map {
      m => m.group(1)
    }

    matched.toSeq.headOption.getOrElse(throw new IllegalArgumentException("Invalid authorization header"))
  }

  private def sign(date: String, path: String): String = {
    val input = List[String](date, path)
    val toSign = input.mkString("\n")
    calculateHMAC("secret", toSign)
  }


  private def calculateHMAC(secret: String, toEncode: String): String = {
    val signingKey = new SecretKeySpec(secret.getBytes, ALGORITHM)
    val mac = Mac.getInstance(ALGORITHM)
    mac.init(signingKey)
    val rawHmac = mac.doFinal(toEncode.getBytes)
    new String(Base64.encodeBase64(rawHmac))
  }

  def main(args: Array[String]) {
    if (args.length < 2) throw new IllegalArgumentException("date (dd-MM-yyyy'T'HH:mm:ss'Z') and path are required as args")

    val date = args(0)
    val path = args(1)

    println(sign(date, path))
  }
}
