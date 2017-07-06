package controllers

import javax.inject.{Inject, Singleton}

import actions.AuthenticatedAction
import com.gu.identity.util.Logging
import com.gu.tip.Tip
import configuration.Config
import models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import services._

import scala.concurrent.Future

import scalaz._
import scalaz.std.scalaFuture._
import scalaz.std.string._
import scalaz.syntax.validation._
import scalaz.syntax.apply._
import scalaz.syntax.std.boolean._

import models.ApiError._
import models._

class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)

@Singleton class UsersController @Inject() (
    userService: UserService,
    auth: AuthenticatedAction,
    salesforce: SalesforceService,
    discussionService: DiscussionService,
    exactTargetService: ExactTargetService) extends Controller with Logging {

  def search(query: String, limit: Option[Int], offset: Option[Int]) = auth.async { request =>
    import Config.SearchValidation._

    val queryValid =
      (query.length < minimumQueryLength) ? s"query must be a minimum of $minimumQueryLength characters".failure[String] | query.success[String]

    val limitValid =
      limit.exists(_ < 0) ? "limit must be a positive integer".failure[Option[Int]] | limit.success[String]

    val offsetValid =
      offset.exists(_ < 0) ? "offset must be a positive integer".failure[Option[Int]] | offset.success[String]

    (queryValid |@| limitValid |@| offsetValid) { (query, limit, offset) =>
      EitherT(userService.search(query, limit, offset)).fold(
        error => InternalServerError(error),
        response => Ok(Json.toJson(response))
      )
    } match {
      case Success(result) => result
      case Failure(error) => Future.successful(BadRequest(ApiError(error)))
    }
  }

  def unreserveEmail(id: String) = auth.async { request =>
    userService.unreserveEmail(id).map(_ => NoContent)
  }

  private def UserAction(userId: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {

      def findUserById(userId: String): Future[Result \/ User] =
        EitherT(userService.findById(userId)).fold(
          error => -\/(InternalServerError(error)),
          userOpt => userOpt match {
            case Some(user) => \/-(user)
            case None => -\/(NotFound)
          }
        )

      def enrichUserWithProducts(user: User): Future[Result \/ User]  =
        EitherT(userService.enrichUserWithProducts(user)).leftMap(InternalServerError(_)).run

      (for {
        user <- EitherT(findUserById(userId))
        userWithProducts <- EitherT(enrichUserWithProducts(user))
      } yield {
        if (Config.stage == "PROD") Tip.verify("User Retrieval")
        new UserRequest(userWithProducts, input)
      }).run.map(_.toEither)

    }
  }

  private def OrphanUserAction(email: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {
      val subOrphanOptF = EitherT(salesforce.getSubscriptionByEmail(email))
      val newsOrphanOptF = EitherT(exactTargetService.newslettersSubscriptionByEmail(email))

      val orphanEitherT =
        for {
          subOrphanOpt <- subOrphanOptF
          newsOrphanOpt <- newsOrphanOptF
        } yield {
          if (subOrphanOpt.isDefined)
            Some(new UserRequest(User(orphan = true, id = "orphan", email = email, subscriptionDetails = Some(subOrphanOpt.get)), input))
          else if (newsOrphanOpt.isDefined) {
            Some(new UserRequest(User(orphan = true, id = "orphan", email = email, newslettersSubscription = Some(newsOrphanOpt.get)), input))
          }
          else
            None
        }

      orphanEitherT.fold(
        error => Left(InternalServerError(error)),
        orphanOpt => orphanOpt.fold[Either[Result, UserRequest[A]]]
          (Left(NotFound))
          (orphan => Right(orphan))
      )
    }
  }

  def findById(id: String) = (auth andThen UserAction(id)) { request =>
    request.user
  }

  def findOrphanByEmail(email: String) = (auth andThen OrphanUserAction(email)) { request =>
    request.user
  }

  def update(id: String) = (auth andThen UserAction(id)).async(parse.json) { request =>
    request.body.validate[UserUpdateRequest] match {
      case JsSuccess(result, path) =>
        UserUpdateRequestValidator.isValid(result).fold(
          e => Future(BadRequest(ApiError("Failed to update user", e.message))),
          validUserUpdateRequest => {
            EitherT(userService.update(request.user, validUserUpdateRequest)).fold(
              error => InternalServerError(error),
              user => {
                if (Config.stage == "PROD") Tip.verify("User Update")
                Ok(Json.toJson(user))
              }
            )
          }
        )

      case JsError(e) => Future(BadRequest(ApiError("Failed to update user", e.toString)))
    }
  }

  def delete(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Deleting user $id")

    val deleteEmailSubscriberF = EitherT(exactTargetService.deleteSubscriber(request.user.email))
    val deleteAccountF = EitherT(userService.delete(request.user))

    (for {
      _ <- deleteEmailSubscriberF
      _ <- deleteAccountF
    } yield {
      EmailService.sendDeletionConfirmation(request.user.email)
    }).fold(
      error => {
        logger.error(s"Error deleting user $id: $error")
        InternalServerError(error)
      },
      _ => {
        logger.info(s"Successfully deleted user $id")
        NoContent
      }
    )
  }

  def unsubcribeFromAllEmailLists(email: String) = auth.async { request =>
    logger.info("Unsubscribing from all email lists (marketing and editorial)")
    val unsubscribeMarketingEmailsInIdentity = EitherT(exactTargetService.unsubscribeFromAllLists(email))
    val unsubcribeAllEmailsInExactTarget = EitherT(userService.unsubscribeFromMarketingEmails(email))

    (for {
      _ <- unsubscribeMarketingEmailsInIdentity
      _ <- unsubcribeAllEmailsInExactTarget
    } yield ()).fold(
      error => {
        logger.error(s"Failed to unsubscribe from all email lists: $error")
        InternalServerError(error)
      },
      _ => {
        logger.info(s"Successfully unsubscribed from all email lists")
        NoContent
      }
    )
  }

  def activateEmailSubscriptions(email: String) = auth.async { request =>
    logger.info("Activate email address in ExactTarget")

    EitherT(exactTargetService.activateEmailSubscription(email)).fold(
      error => {
        logger.error(s"Failed to activate email subscriptions: $error")
        InternalServerError(error)
      },
      _ => {
        logger.info(s"Successfully activated email subscriptions")
        NoContent
      }
    )
  }

  def sendEmailValidation(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Sending email validation for user with id: $id")
    EitherT(userService.sendEmailValidation(request.user)).fold(
      error => InternalServerError(error),
      _ => NoContent
    )
  }

  def validateEmail(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Validating email for user with id: $id")
    EitherT(userService.validateEmail(request.user)).fold(
      error => InternalServerError(error),
      _ => NoContent
    )
  }
}
