package services

import javax.inject.{Inject, Singleton}

import com.exacttarget.fuelsdk._
import com.gu.identity.util.Logging
import configuration.Config

import scala.concurrent.Future
import scalaz.std.scalaFuture._
import scalaz.{OptionT, \/}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import repositories.UsersReadRepository

import scala.collection.JavaConversions._

@Singleton
class ExactTargetService @Inject() (usersReadRepository: UsersReadRepository) extends Logging {

  type UnsubscribeError = ETResponse[ETSubscriber]

  def unsubscribeFromAllLists(email: String): Future[UnsubscribeError \/ ETSubscriber] = Future {
    logger.info("Unsubscribing user from all emailing lists")

    def unsubscribe(subscriber: ETSubscriber) = {
      subscriber.setStatus(ETSubscriber.Status.UNSUBSCRIBED)
      val response = etClientAdmin.update(subscriber)

      Option(response.getResult).fold[UnsubscribeError \/ ETSubscriber]
        {\/.left(response)}
        {result => \/.right(result.getObject)}
    }

    def createAndUnsubscribe() = {
      val subscriber = new ETSubscriber()
      subscriber.setEmailAddress(email)
      subscriber.setKey(email)
      subscriber.setStatus(ETSubscriber.Status.UNSUBSCRIBED)
      val response = etClientAdmin.create(subscriber)

      Option(response.getResult).fold[UnsubscribeError \/ ETSubscriber]
        {\/.left(response)}
        {result => \/.right(result.getObject)}
    }

    Option(
      etClientAdmin.retrieve(classOf[ETSubscriber], s"emailAddress=$email").getResult
    ).fold(createAndUnsubscribe)(result => unsubscribe(result.getObject))
  }

  def updateEmailAddress(oldEmail: String, newEmail: String) = Future {
    logger.info("Updating user's email address in ExactTarget")
    Option(etClientAdmin.retrieve(classOf[ETSubscriber], s"emailAddress=$oldEmail").getResult).map { result =>
      val subscriber = result.getObject
      subscriber.setEmailAddress(newEmail)
      etClientAdmin.update(subscriber)
    }
  }

  def listOfSubscriptions(identityId: String): Future[List[String]] =
    OptionT(usersReadRepository.findById(identityId)).fold(
      user => {
        val response = etClientEditorial.retrieve(classOf[ETSubscriber], s"key=${user.email}")
        Option(response.getResult).fold[List[String]]
          { Nil }
          { result => result.getObject.getSubscriptions.toList.map(_.getListId) }
      },
      Nil
    )

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