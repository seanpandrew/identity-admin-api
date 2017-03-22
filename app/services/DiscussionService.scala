package services

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import configuration.Config
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

case class DiscussionStats(status: String, comments: Int, pickedComments: Int)

object DiscussionStats {
  implicit val format = Json.format[DiscussionStats]
}

@Singleton class DiscussionService @Inject() (ws: WSClient) extends Logging {

  def hasCommented(id: String): Future[Boolean] = {
    ws.url(s"${Config.Discussion.apiUrl}/profile/$id/stats").get().map { response =>
      val stats = Json.parse(response.body).as[DiscussionStats]
      stats.comments > 0
    }.recover { case _ => false }
  }
}

