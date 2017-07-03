package services

import javax.inject.{Inject, Singleton}
import com.exacttarget.fuelsdk._
import com.gu.identity.util.Logging
import configuration.Config
import models.{ApiError, ApiResponse, NewslettersSubscription}
import scala.concurrent.Future
import scalaz.std.scalaFuture._
import scalaz.{OptionT, \/, \/-}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import repositories.UsersReadRepository
import scala.collection.JavaConversions._

@Singleton class ExactTargetService @Inject() (usersReadRepository: UsersReadRepository) extends Logging {
  /**
    * Unsubscribe this subscriber from all current and future subscriber lists.
    */
  def unsubscribeFromAllLists(email: String): Future[ApiError \/ ETSubscriber] = {
    logger.info("Unsubscribe from all email lists")
    updateSubscriptionStatus(email, ETSubscriber.Status.UNSUBSCRIBED)
  }

  /**
    * Allows this subscriber to subscribe to lists in the future. This will only activate the subscriber
    * on the All Subscribers list, and not on any specific lists.
    */
  def activateEmailSubscription(email: String): Future[ApiError \/ ETSubscriber] = {
    logger.info("Activate email subscriptions")
    updateSubscriptionStatus(email, ETSubscriber.Status.ACTIVE)
  }

  private def updateSubscriptionStatus(
      email: String, status: ETSubscriber.Status): Future[ApiError \/ ETSubscriber] = Future {

    def updateStatus(subscriber: ETSubscriber) = {
      subscriber.setStatus(status)
      val response = etClientAdmin.update(subscriber)

      Option(response.getResult).fold[ApiError \/ ETSubscriber]
        {\/.left(ApiError("Failed to update email status", response.getResponseMessage))}
        {result => \/.right(result.getObject)}
    }

    def createAndUpdateStatus() = {
      val subscriber = new ETSubscriber()
      subscriber.setEmailAddress(email)
      subscriber.setKey(email)
      subscriber.setStatus(ETSubscriber.Status.UNSUBSCRIBED)
      val response = etClientAdmin.create(subscriber)

      Option(response.getResult).fold[ApiError \/ ETSubscriber]
        {\/.left(ApiError("Failed to update email status", response.getResponseMessage))}
        {result => \/.right(result.getObject)}
    }

    Option(
      etClientAdmin.retrieve(classOf[ETSubscriber], s"emailAddress=$email").getResult
    ).fold(createAndUpdateStatus)(result => updateStatus(result.getObject))
  }

  def updateEmailAddress(oldEmail: String, newEmail: String) = Future {
    logger.info("Updating user's email address in ExactTarget")
    Option(etClientAdmin.retrieve(classOf[ETSubscriber], s"emailAddress=$oldEmail").getResult).map { result =>
      val subscriber = result.getObject
      subscriber.setEmailAddress(newEmail)
      etClientAdmin.update(subscriber)
    }
  }

  def newslettersSubscriptionByIdentityId(identityId: String): ApiResponse[Option[NewslettersSubscription]] =
    OptionT(usersReadRepository.findById(identityId)).fold(
      user => newslettersSubscriptionByEmail(user.email),
      Future.successful(\/-(None))
    ).flatMap(identity)

  def newslettersSubscriptionByEmail(email: String): ApiResponse[Option[NewslettersSubscription]] = Future {
    \/-(Option(etClientEditorial.retrieve(classOf[ETSubscriber], s"key=$email").getResult) match {
      case None => None
      case Some(result) => Some(NewslettersSubscription(
        status = result.getObject.getStatus.value(),
        list = result.getObject.getSubscriptions.toList.filter(_.getStatus == ETSubscriber.Status.ACTIVE).map(_.getListId)))
    })
  }

  private lazy val etClientAdmin = {
    val etConf = new ETConfiguration()
    etConf.set("clientId", Config.ExactTarget.Admin.clientId)
    etConf.set("clientSecret", Config.ExactTarget.Admin.clientSecret)
    new ETClient(etConf)
  }

  private lazy val etClientEditorial = {
    val etConf = new ETConfiguration()
    etConf.set("clientId", Config.ExactTarget.Editorial.clientId)
    etConf.set("clientSecret", Config.ExactTarget.Editorial.clientSecret)
    new ETClient(etConf)
  }
}