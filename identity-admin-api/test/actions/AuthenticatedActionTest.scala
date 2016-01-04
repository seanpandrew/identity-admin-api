package actions

import models.ApiErrors
import org.joda.time.{DateTimeZone, DateTime}
import org.mockito.Mockito
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import play.api.http.HeaderNames
import play.api.mvc.{Results, Request, Result}
import play.api.test.FakeRequest
import util.Formats

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.mockito.Mockito._
import play.api.test.Helpers._
import play.api.libs.json.Json

class AuthenticatedActionTest extends WordSpec with Matchers with BeforeAndAfter {

  class TestAuthenticatedAction extends AuthenticatedAction {
    val secret = "secret"
  }

  val action = spy(new TestAuthenticatedAction)

  before {
    Mockito.reset(action)
  }

  "extractToken" should {
    "Return None if invalid format" in {
      val headerValue = "123456"
      action.extractToken(headerValue) shouldEqual None
    }

    "Return Some(token) from headerValue" in {
      val token = "xQh2D+jlEZh7tEBv0IwD10mklB5RRop665kRChbdZow="
      val headerValue = s"HMAC $token"

      action.extractToken(headerValue) shouldEqual Some(token)
    }
  }

  "invokeBlock" should {

    def block(request: Request[Any]): Future[Result] = Future.successful(Results.Ok)

    val dateHeaderValue = Formats.toHttpDateTimeString(DateTime.now)

    def verifyUnauthorized(result: Future[Result], errorMessage: String) = {
      status(result) shouldEqual UNAUTHORIZED
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.unauthorized(errorMessage))
    }

    "Return unauthorised if Date header is missing" in {
      val request = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> "HMAC 1234")
      val result = action.invokeBlock(request, block)
      verifyUnauthorized(result, "Date header is required.")
    }

    "Return unauthorised if Authorization header is missing" in {
      val request = FakeRequest().withHeaders(HeaderNames.DATE -> dateHeaderValue)
      val result = action.invokeBlock(request, block)
      verifyUnauthorized(result, "Authorization header is required.")
    }

    "Return unauthorised if HMAC token cannot be extracted from Authorization header" in {
      val authHeaderValue = "auth"
      doReturn(None).when(action).extractToken(authHeaderValue)
      val request = FakeRequest().withHeaders(HeaderNames.DATE -> dateHeaderValue, HeaderNames.AUTHORIZATION -> authHeaderValue)
      val result = action.invokeBlock(request, block)
      verifyUnauthorized(result, "Authorization header is invalid.")
    }

    "Return unauthorised if HMAC token does not match calculated HMAC" in {
      val authHeaderValue = "HMAC 12345"
      val path= "/v1/user/id?param=val"
      doReturn(Some(authHeaderValue)).when(action).extractToken(authHeaderValue)
      doReturn("differentHmac").when(action).sign(dateHeaderValue, path)
      val request = FakeRequest("GET", path).withHeaders(HeaderNames.DATE -> dateHeaderValue, HeaderNames.AUTHORIZATION -> authHeaderValue)
      val result = action.invokeBlock(request, block)
      verifyUnauthorized(result, "Authorization token is invalid.")
    }

    "Return unauthorised if HMAC has expired" in {
      val path= "/v1/user/id?param=val"
      val dateHeader = Formats.toHttpDateTimeString(DateTime.now.plusMinutes(action.HmacValidDurationInMinutes + 1))
      val authHeaderValue = s"HMAC ${action.sign(dateHeaderValue, path)}"
      val request = FakeRequest("GET", path).withHeaders(HeaderNames.DATE -> dateHeader, HeaderNames.AUTHORIZATION -> authHeaderValue)
      val result = action.invokeBlock(request, block)
      verifyUnauthorized(result, "Authorization token is invalid.")
    }

    "Return unauthorised if Date cannot be parsed" in {
      val path= "/v1/user/id?param=val"
      val dateHeader = DateTime.now.withZone(DateTimeZone.forID("GMT")).toString("yyyy-MM-dd'T'HH:mm:ss")
      val authHeaderValue = s"HMAC ${action.sign(dateHeaderValue, path)}"
      val request = FakeRequest("GET", path).withHeaders(HeaderNames.DATE -> dateHeader, HeaderNames.AUTHORIZATION -> authHeaderValue)
      val result = action.invokeBlock(request, block)
      verifyUnauthorized(result, "Authorization token is invalid.")
    }

    "Execute block if auhorization header value matches calculated signed request" in {
      val path= "/v1/user/id?param=val"
      val authHeaderValue = s"HMAC ${action.sign(dateHeaderValue, path)}"
      val request = FakeRequest("GET", path).withHeaders(HeaderNames.DATE -> dateHeaderValue, HeaderNames.AUTHORIZATION -> authHeaderValue)
      Await.result(action.invokeBlock(request, block), 1.second) shouldEqual Results.Ok
    }
  }
}
