package services

import javax.inject.Inject

import com.gu.identity.util.Logging
import configuration.Config
import models.ApiError
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.Future

case class IdentityApiError(message: String, description: String, context: Option[String] = None)

object IdentityApiError {
  implicit val format = Json.format[IdentityApiError]
}

case class IdentityApiErrorResponse(status: String, errors: List[IdentityApiError])

object IdentityApiErrorResponse {
  implicit val format = Json.format[IdentityApiErrorResponse]
}

class IdentityApiClient @Inject() (ws: WSClient) extends Logging {

  val baseUrl = Config.IdentityApi.baseUrl
  val clientToken = Config.IdentityApi.clientToken
  private val ClientTokenHeaderName = "X-GU-ID-Client-Access-Token"
  private lazy val clientTokenHeaderValue = s"Bearer $clientToken"
  
  private def sendEmailValidationUrl(userId: String): String = s"$baseUrl/user/$userId/send-validation-email"
  
  private def addAuthHeaders(req: WSRequest): WSRequest = {
    req.withHeaders(ClientTokenHeaderName -> clientTokenHeaderValue)
  }
  
  def sendEmailValidation(userId: String): Future[Either[ApiError, Boolean]] = {
    addAuthHeaders(ws.url(sendEmailValidationUrl(userId))).post("").map(
      response => 
        if(response.status == 200)
            Right(true)
        else {
          val errorResponse = Json.parse(response.body).as[IdentityApiErrorResponse]
          val errorMessage = errorResponse.errors.headOption.map(x => x.message).getOrElse("Unknown error")
          Left(ApiError("Send Email Validation Error", errorMessage))
        }
    ).recover { case e: Any =>
      val title = "Could not send email validation"
      logger.error(title, e.getMessage)
      Left(ApiError(title, e.getMessage))
    }
  }

}
