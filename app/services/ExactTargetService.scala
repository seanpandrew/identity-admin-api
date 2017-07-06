package services

import javax.inject.{Inject, Singleton}

import com.exacttarget.fuelsdk._
import com.gu.identity.util.Logging
import configuration.Config
import models.{ApiError, ApiResponse, ExactTargetSubscriber, NewslettersSubscription}

import scala.concurrent.Future
import scalaz.std.scalaFuture._
import scalaz.{-\/, EitherT, \/, \/-}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import repositories.UsersReadRepository

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

@Singleton class ExactTargetService @Inject() (usersReadRepository: UsersReadRepository) extends Logging {
  /**
    * Unsubscribe this subscriber from all current and future subscriber lists.
    */
  def unsubscribeFromAllLists(email: String): ApiResponse[ETSubscriber] = {
    logger.info("Unsubscribe from all email lists")
    updateSubscriptionStatus(email, ETSubscriber.Status.UNSUBSCRIBED)
  }

  /**
    * Allows this subscriber to subscribe to lists in the future. This will only activate the subscriber
    * on the All Subscribers list, and not on any specific lists.
    */
  def activateEmailSubscription(email: String): ApiResponse[ETSubscriber] = {
    logger.info("Activate email subscriptions")
    updateSubscriptionStatus(email, ETSubscriber.Status.ACTIVE)
  }

  private def updateSubscriptionStatus(
      email: String, status: ETSubscriber.Status): ApiResponse[ETSubscriber] = Future {

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
      subscriber.setStatus(status)
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
    EitherT(usersReadRepository.find(identityId)).fold(
      error => Future.successful(-\/(error)),
      userOpt => userOpt match {
        case Some(user) => newslettersSubscriptionByEmail(user.email)
        case None => Future.successful(\/-(None))
      }
    ).flatMap(identity)

  def newslettersSubscriptionByEmail(email: String): ApiResponse[Option[NewslettersSubscription]] = Future {

    def activeNewsletterSubscriptions(subscriptions: List[ETSubscriber#Subscription]) =
      subscriptions.filter(_.getStatus == ETSubscriber.Status.ACTIVE).map(_.getListId)

    \/-(Option(etClientEditorial.retrieve(classOf[ETSubscriber], s"emailAddress=$email").getResult) match {
      case Some(result) =>
        val subscriber = result.getObject
        val editorialSubscriberStatus = subscriber.getStatus

        if (editorialSubscriberStatus == ETSubscriber.Status.ACTIVE)
          Some(NewslettersSubscription(activeNewsletterSubscriptions(subscriber.getSubscriptions.toList)))
        else
          None

      case None => None
    })
  }

  def deleteSubscriber(email: String): ApiResponse[Option[ETResponse[ETSubscriber]]] = Future {
    val deleteTry = Try {
      Option(etClientAdmin.retrieve(classOf[ETSubscriber], s"emailAddress=$email").getResult) match {
        case Some(result) =>
          val subscriber = result.getObject
          \/-(Some(etClientAdmin.delete(subscriber)))

        case None => \/-(None)
      }
    }

    deleteTry match {
      case Success(result) => result

      case Failure(error) =>
        val title = "Failed to delete subscriber from ExactTarget"
        logger.error(title, error)
        -\/(ApiError(title, error.getMessage))
    }
  }

  def status(email: String): ApiResponse[Option[String]] = Future {
    \/-(Option(etClientAdmin.retrieve(classOf[ETSubscriber], s"emailAddress=$email").getResult) match {
      case Some(result) =>
        val subscriber = result.getObject
        Some(subscriber.getStatus.value())

      case None => None
    })
  }

  def subscriberByEmail(email: String): ApiResponse[Option[ExactTargetSubscriber]] = {
    val statusF = EitherT(status(email))
    val newslettersF = EitherT(newslettersSubscriptionByEmail(email))

    (for {
      statusOpt <- statusF
      newslettersOpt <- newslettersF
    } yield {
      statusOpt match {
        case Some(status) => Some(ExactTargetSubscriber(status, newslettersOpt))
        case None => None
      }
    }).run
  }

  def subscriberByIdentityId(identityId: String): ApiResponse[Option[ExactTargetSubscriber]] = {
    EitherT(usersReadRepository.find(identityId)).fold(
      error => Future.successful(-\/(error)),
      userOpt => userOpt match {
        case Some(user) => subscriberByEmail(user.email)
        case None => Future.successful(\/-(None))
      }
    ).flatMap(identity)
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