package controllers

import javax.inject.{Inject, Singleton}

import actions.AuthenticatedAction
import com.gu.identity.util.Logging
import com.gu.tip.Tip
import configuration.Config
import models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import services._

import scala.concurrent.Future
import scalaz.{-\/, EitherT, OptionT, \/-}
import scalaz.std.scalaFuture._
import models.ApiError._
import models._

class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class UsersController @Inject() (
    userService: UserService,
    auth: AuthenticatedAction,
    salesforce: SalesforceService,
    discussionService: DiscussionService,
    exactTargetService: ExactTargetService) extends Controller with Logging {

  private val MinimumQueryLength = 2

  def search(query: String, limit: Option[Int], offset: Option[Int]) = auth.async { request =>
    if (offset.exists(_ < 0)) {
      Future.successful(BadRequest(ApiError("offset must be a positive integer")))
    }
    else if (limit.exists(_ < 0)) {
      Future.successful(BadRequest(ApiError("limit must be a positive integer")))
    }
    else if (query.length < MinimumQueryLength) {
      Future.successful(BadRequest(ApiError(s"query must be a minimum of $MinimumQueryLength characters")))
    }
    else {
      EitherT(userService.search(query, limit, offset)).fold(
        error => InternalServerError(error),
        response => Ok(Json.toJson(response))
      )
    }
  }

  import play.api.libs.json._

  def unreserveEmail(id: String) = auth.async { request =>
    userService.unreserveEmail(id).map(_ => NoContent)
  }

  private def UserAction(userId: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {

      val subscriptionF = salesforce.getSubscriptionByIdentityId(userId)
      val membershipF = salesforce.getMembershipByIdentityId(userId)
      val hasCommentedF = discussionService.hasCommented(userId)
      val newslettersSubF = exactTargetService.newslettersSubscription(userId)

      for {
        user <- userService.findById(userId)
        subscription <- subscriptionF
        membership <- membershipF
        hasCommented <- hasCommentedF
        newslettersSub <- newslettersSubF
      } yield {
        user match {
          case -\/(error) => Left(NotFound)

          case \/-(r) =>
            if (Config.stage == "PROD") Tip.verify("User Retrieval")

            val userWithSubscriptions = r.copy(
              subscriptionDetails = subscription,
              membershipDetails = membership,
              hasCommented = hasCommented,
              newslettersSubscription = newslettersSub)

            Right(new UserRequest(userWithSubscriptions, input))
        }
      }
    }
  }


  private def OrphanUserAction(email: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {
      OptionT(salesforce.getSubscriptionByEmail(email)).fold(
        sub => Right(new UserRequest(User(orphan = true, id = "orphan", email = sub.email, subscriptionDetails = Some(sub)), input)),
        Left(NotFound)
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
          UserUpdateRequestValidator.isValid(result) match {
            case Right(validUserUpdateRequest) => {
              if (Config.stage == "PROD") Tip.verify("User Update")
              logger.info(s"Updating user id:$id, body: $result")
              EitherT(userService.update(request.user, validUserUpdateRequest)).fold(
                error => InternalServerError(error),
                user => Ok(Json.toJson(user))
              )
            }
            case Left(e) => Future(BadRequest(ApiError("Failed to update user", e.message)))
          }
        case JsError(e) => Future(BadRequest(ApiError("Failed to update user", e.toString)))
      }
  }

  def delete(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Deleting user $id")

    def unsubscribeEmails() = EitherT(exactTargetService.unsubscribeFromAllLists(request.user.email))
    def deleteAccount() = EitherT(userService.delete(request.user))

    (for {
      _ <- unsubscribeEmails()
      _ <- deleteAccount()
    } yield EmailService.sendDeletionConfirmation(request.user.email)).fold(
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
    userService.sendEmailValidation(request.user).map {
      case \/-(r) => NoContent
      case -\/(error) => InternalServerError(error)
    }
  }

  def validateEmail(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Validating email for user with id: $id")
    userService.validateEmail(request.user).map {
      case \/-(r) => NoContent
      case -\/(error) => InternalServerError(error)
    }
  }
}
