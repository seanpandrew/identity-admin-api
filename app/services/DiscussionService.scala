package services

import javax.inject.{Inject, Singleton}
import com.gu.identity.util.Logging
import configuration.Config
import models.{ApiError, ApiResponse}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scalaz.{-\/, \/-}

case class DiscussionStats(status: String, comments: Int, pickedComments: Int)

object DiscussionStats {
  implicit val format = Json.format[DiscussionStats]
}

@Singleton class DiscussionService @Inject() (ws: WSClient) extends Logging {
  def hasCommented(id: String): ApiResponse[Boolean] = {
    ws.url(s"${Config.Discussion.apiUrl}/profile/$id/stats").get().map { response =>
      Json.parse(response.body).validate[DiscussionStats].fold(
        error => -\/(ApiError("Failed to determine if user has commented due to Json parsing error", error.toString)),
        discussionStats => \/-(discussionStats.comments > 0)
      )
    }.recover { case error => -\/(ApiError("Failed to communicate with DAPI", error.getMessage)) }
  }
}

