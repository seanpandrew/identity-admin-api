package services

import javax.inject.{Inject, Singleton}

import com.exacttarget.fuelsdk._
import com.gu.identity.util.Logging
import configuration.Config
import models.{ApiError, ApiResponse, ExactTargetSubscriber, NewslettersSubscription}

import scala.concurrent.Future
import scalaz.std.scalaFuture._
import scalaz.{-\/, EitherT, \/-}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import repositories.UsersReadRepository

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

@Singleton class ExactTargetService @Inject() (usersReadRepository: UsersReadRepository) extends Logging {
  /**
    * Unsubscribe this subscriber from all current and future subscriber lists.
    */
  def unsubscribeFromAllLists(email: String): ApiResponse[ETResponse[ETSubscriber]] = {
    updateSubscriptionStatus(email, ETSubscriber.Status.UNSUBSCRIBED)
  }

  /**
    * Allows this subscriber to subscribe to lists in the future. This will only activate the subscriber
    * on the All Subscribers list, and not on any specific lists.
    */
  def activateEmailSubscription(email: String): ApiResponse[ETResponse[ETSubscriber]] = {
    updateSubscriptionStatus(email, ETSubscriber.Status.ACTIVE)
  }

  private def updateSubscriptionStatus(
      email: String, status: ETSubscriber.Status): ApiResponse[ETResponse[ETSubscriber]] = {

    def updateStatus(subscriber: ETSubscriber) = {
      subscriber.setStatus(status)
      etClientAdmin.update(subscriber)
    }

    def createAndUpdateStatus() = {
      val subscriber = new ETSubscriber()
      subscriber.setEmailAddress(email)
      subscriber.setKey(email)
      subscriber.setStatus(status)
      etClientAdmin.create(subscriber)
    }

    EitherT(retrieveSubscriber("emailaddress", email, etClientAdmin)).map {
      case Some(subscriber) => updateStatus(subscriber)
      case None => createAndUpdateStatus()
    }.run

  }

  def updateEmailAddress(oldEmail: String, newEmail: String): ApiResponse[Option[ETResponse[ETSubscriber]]] =
    EitherT(retrieveSubscriber("emailAddress", oldEmail, etClientAdmin)).map {
      case Some(subscriber) => Some {
        subscriber.setEmailAddress(newEmail)
        etClientAdmin.update(subscriber)
      }

      case None => None
    }.run

  def newslettersSubscriptionByIdentityId(identityId: String): ApiResponse[Option[NewslettersSubscription]] =
    EitherT(usersReadRepository.find(identityId)).fold(
      error => Future.successful(-\/(error)),
      userOpt => userOpt match {
        case Some(user) => newslettersSubscriptionByEmail(user.email)
        case None => Future.successful(\/-(None))
      }
    ).flatMap(identity)

  def newslettersSubscriptionByEmail(email: String): ApiResponse[Option[NewslettersSubscription]] = {

    def activeNewsletterSubscriptions(subscriptions: List[ETSubscriber#Subscription]) =
      subscriptions.filter(_.getStatus == ETSubscriber.Status.ACTIVE).map(_.getListId)

    def subscriberIsActive(subscriber: ETSubscriber) = subscriber.getStatus == ETSubscriber.Status.ACTIVE

    EitherT(retrieveSubscriber("emailAddress", email, etClientEditorial)).map {
      case Some(subscriber) => {
        val activeList = activeNewsletterSubscriptions(subscriber.getSubscriptions.toList)

        if (subscriberIsActive(subscriber) && !activeList.isEmpty)
          Some(NewslettersSubscription(activeList))
        else
          None
      }

      case None => None
    }.run
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

  def status(email: String): ApiResponse[Option[String]] =
    EitherT(retrieveSubscriber("emailAddress", email, etClientAdmin)).map {
      case Some(subscriber) => Some(subscriber.getStatus.value)
      case None => None
    }.run

  def subscriberByEmail(email: String): ApiResponse[Option[ExactTargetSubscriber]] = {
    val statusF = EitherT(status(email))
    val newslettersF = EitherT(newslettersSubscriptionByEmail(email))

    val subByEmailT =
      for {
        statusOpt <- statusF
        newslettersOpt <- newslettersF
      } yield {
        statusOpt match {
          case Some(status) => Some(ExactTargetSubscriber(status, newslettersOpt))
          case None => None
        }
      }

    subByEmailT.run
  }

  def subscriberByIdentityId(identityId: String): ApiResponse[Option[ExactTargetSubscriber]] =
    EitherT(usersReadRepository.find(identityId)).flatMap {
      case Some(user) => EitherT(subscriberByEmail(user.email))
      case None => EitherT.right(Future.successful(Option.empty[ExactTargetSubscriber]))
    }.run

  private def retrieveSubscriber(
      key: String, value: String, client: ETClient): ApiResponse[Option[ETSubscriber]] = Future {

    val retrieveTry = Try {
      Option(client.retrieve(classOf[ETSubscriber], s"$key=$value").getResult) match {
        case Some(result) => \/-(Some(result.getObject))
        case None => \/-(Option.empty[ETSubscriber])
      }
    }

    retrieveTry match {
      case Success(result) => result

      case Failure(error) =>
        val title = "Failed to retrieve subscriber from ExactTarget"
        logger.error(title, error)
        -\/(ApiError(title, error.getMessage))
    }
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