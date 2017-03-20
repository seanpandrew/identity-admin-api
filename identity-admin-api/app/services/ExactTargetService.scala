package services

import com.exacttarget.fuelsdk.{ETClient, ETConfiguration, ETResponse, ETSubscriber}
import com.gu.identity.util.Logging
import configuration.Config

import scala.concurrent.Future
import scalaz.\/
import scala.concurrent.ExecutionContext.Implicits.global

object ExactTargetService extends Logging {

  type UnsubscribeError = ETResponse[ETSubscriber]

  def unsubscribeFromAllLists(email: String): Future[UnsubscribeError \/ ETSubscriber] = Future {
    logger.info("Unsubscribing user from all emailing lists")

    def unsubscribe(subscriber: ETSubscriber) = {
      subscriber.setStatus(ETSubscriber.Status.UNSUBSCRIBED)
      val response = etClient.update(subscriber)

      Option(response.getResult).fold[UnsubscribeError \/ ETSubscriber]
        {\/.left(response)}
        {result => \/.right(result.getObject)}
    }

    def createAndUnsubscribe() = {
      val subscriber = new ETSubscriber()
      subscriber.setEmailAddress(email)
      subscriber.setKey(email)
      subscriber.setStatus(ETSubscriber.Status.UNSUBSCRIBED)
      val response = etClient.create(subscriber)

      Option(response.getResult).fold[UnsubscribeError \/ ETSubscriber]
        {\/.left(response)}
        {result => \/.right(result.getObject)}
    }

    Option(
      etClient.retrieve(classOf[ETSubscriber], s"emailAddress=$email").getResult
    ).fold(createAndUnsubscribe)(result => unsubscribe(result.getObject))
  }

  private lazy val etClient = {
    val etConf = new ETConfiguration()
    etConf.set("clientId", Config.ExactTarget.clientId)
    etConf.set("clientSecret", Config.ExactTarget.clientSecret)
    new ETClient(etConf)
  }
}