package services

import com.google.inject.ImplementedBy
import com.gu.identity.util.Logging
import configuration.Config
import models.{ApiError, ApiErrors}
import play.api.Play._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSRequest}

import scala.concurrent.Future

case class IdentityApiError(message: String, description: String, context: Option[String] = None)

object IdentityApiError {
  implicit val format = Json.format[IdentityApiError]
}

case class IdentityApiErrorResponse(status: String, errors: List[IdentityApiError])

object IdentityApiErrorResponse {
  implicit val format = Json.format[IdentityApiErrorResponse]
}

class IdentityApiClientWithConfig extends IdentityApiClient {
  override val baseUrl = Config.IdentityApi.baseUrl
  override val clientToken = Config.IdentityApi.clientToken
}

@ImplementedBy(classOf[IdentityApiClientWithConfig])
trait IdentityApiClient extends Logging {
  
  val baseUrl: String
  val clientToken: String
  private val ClientTokenHeaderName = "X-GU-ID-Client-Access-Token"
  private lazy val clientTokenHeaderValue = s"Bearer $clientToken"
  
  private def sendEmailValidationUrl(userId: String): String = s"$baseUrl/user/$userId/send-validation-email"
  
  private def addAuthHeaders(req: WSRequest): WSRequest = {
    req.withHeaders(ClientTokenHeaderName -> clientTokenHeaderValue)
  }
  
  def sendEmailValidation(userId: String): Future[Either[ApiError, Boolean]] = {
    addAuthHeaders(WS.url(sendEmailValidationUrl(userId))).post("").map(
      response => 
        if(response.status == 200)
            Right(true)
        else {
          val errorResponse = Json.parse(response.body).as[IdentityApiErrorResponse]
          val errorMessage = errorResponse.errors.headOption.map(x => x.message).getOrElse("Unknown error")
          Left(ApiErrors.internalError(errorMessage))
        }
    ).recover { case e: Any =>
      logger.error("Could not send email validation", e.getMessage)
      Left(ApiErrors.internalError(e.getMessage))
    }
  }

}
