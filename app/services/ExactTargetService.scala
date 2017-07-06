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
  def unsubscribeFromAllLists(email: String): ApiResponse[Unit] = {
    updateSubscriptionStatus(email, ETSubscriber.Status.UNSUBSCRIBED)
  }

  /**
    * Allows this subscriber to subscribe to lists in the future. This will only activate the subscriber
    * on the All Subscribers list, and not on any specific lists.
    */
  def activateEmailSubscription(email: String): ApiResponse[Unit] = {
    updateSubscriptionStatus(email, ETSubscriber.Status.ACTIVE)
  }

  def updateEmailAddress(oldEmail: String, newEmail: String): ApiResponse[Unit] =
    EitherT(retrieveSubscriber("emailAddress", oldEmail, etClientAdmin)).flatMap {
      case Some(subscriber) =>
        subscriber.setEmailAddress(newEmail)
        EitherT(updateSubscriber(subscriber))

      case None => EitherT.right(Future.successful({}))
    }.run

  def newslettersSubscriptionByIdentityId(identityId: String): ApiResponse[Option[NewslettersSubscription]] =
    EitherT(usersReadRepository.find(identityId)).flatMap {
      case Some(user) => EitherT(newslettersSubscriptionByEmail(user.email))
      case None => EitherT.right(Future.successful(Option.empty[NewslettersSubscription]))
    }.run

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

  def deleteSubscriber(email: String): ApiResponse[Unit] =
    EitherT(retrieveSubscriber("emailAddress", email, etClientAdmin)).flatMap {
      case Some(subscriber) => EitherT(deleteSubscriber(subscriber))
      case None => EitherT.right(Future.successful({}))
    }.run

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

  private def updateSubscriptionStatus(
    email: String, status: ETSubscriber.Status): ApiResponse[Unit] = {

    def updateStatus(subscriber: ETSubscriber) = {
      subscriber.setStatus(status)
      updateSubscriber(subscriber)
    }

    def createAndUpdateStatus() = {
      val subscriber = new ETSubscriber()
      subscriber.setEmailAddress(email)
      subscriber.setKey(email)
      subscriber.setStatus(status)
      createSubscriber(subscriber)
    }

    EitherT(retrieveSubscriber("emailAddress", email, etClientAdmin)).flatMap {
      case Some(subscriber) => EitherT(updateStatus(subscriber))
      case None => EitherT(createAndUpdateStatus())
    }.run
  }

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

  private def deleteSubscriber(subscriber: ETSubscriber): ApiResponse[Unit] = Future {
    val etResponse = etClientAdmin.delete(subscriber)
    handleETResponse(etResponse, "Failed to delete ExactTarget subscriber")
  }

  private def updateSubscriber(subscriber: ETSubscriber): ApiResponse[Unit] = Future {
    val etResponse = etClientAdmin.update(subscriber)
    handleETResponse(etResponse, "Failed to update ExactTarget subscriber")
  }

  private def createSubscriber(subscriber: ETSubscriber): ApiResponse[Unit] = Future {
    val etResponse = etClientAdmin.create(subscriber)
    handleETResponse(etResponse, "Failed to create ExactTarget subscriber")
  }

  private def handleETResponse(etResponse: ETResponse[ETSubscriber], title: String): ApiError \/ Unit =
    if (etResponse.getResponseCode == "OK")
      \/-{}
    else {
      logger.error(s"${title}: ${etResponse.getResponseMessage}")
      -\/(ApiError(title, etResponse.getResponseMessage))
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