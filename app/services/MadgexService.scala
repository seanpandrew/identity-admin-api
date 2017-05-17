package services

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import configuration.Config
import models.{GNMMadgexUser, MadgexUser, User}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import play.api.libs.json.Json

@Singleton class MadgexService @Inject() (ws: WSClient, requestSigner: RequestSigner) extends Logging {

  implicit val format = Json.format[MadgexUser]

  def sendUpdate(user: GNMMadgexUser): Future[Boolean] = {
    val id = user.id
    requestSigner.sign(ws.url(s"${Config.Madgex.apiUrl}/updatessouser/$id"))
      .post(Json.toJson(user.madgexUser))
      .map { response =>
        response.status match {
          case 200 => true
          case 401 => logger.error("Failed to authenticate with Madgex API.")
            false
          case status => logger.error(s"Unexpected status code:  $status from Madgex API")
            false
        }
      }.recover { case t: Throwable => logger.error("Error when updating Madgex", t)
      false
    }
  }

  def update(user: User): Future[Boolean] = {
    sendUpdate(user)
  }

  implicit def toMadgexUser(user: User): GNMMadgexUser = {
    GNMMadgexUser(user.id, MadgexUser(user.email, user.personalDetails.firstName, user.personalDetails.lastName,
      user.status.receive3rdPartyMarketing.getOrElse(false), user.status.receiveGnmMarketing.getOrElse(false))
    )
  }

}

