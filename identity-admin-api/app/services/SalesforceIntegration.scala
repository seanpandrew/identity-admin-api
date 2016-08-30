package services

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.{CreateQueueRequest, SendMessageRequest, SendMessageResult}
import play.api.libs.json.Json
import com.gu.identity.util.Logging
import configuration.Config
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object SalesforceIntegration extends Logging {
  private val queueName = Config.IdentitySalesforceQueue.name
  private val queueUrl = Config.IdentitySalesforceQueue.url
  private val sqsClient = new AmazonSQSClient(Config.IdentitySalesforceQueue.credentials)
  sqsClient.setRegion(Region.getRegion(Regions.EU_WEST_1))

  def enqueueUserUpdate(userId: String, email: String): Future[SendMessageResult] =
    Future {
      val payload = Json.obj(
        "id" -> userId,
        "userDetails" -> Json.obj(
          "primaryEmailAddress" -> email
        )
      ).toString()

      sqsClient.sendMessage(new SendMessageRequest(queueUrl, payload))
    }.andThen {
      case Success(_) => logger.info(s"Successfully enqueued $userId user update on $queueName")
      case Failure(e) => logger.error(s"Failed to enqueue $userId user update on $queueName", e)
    }
}
