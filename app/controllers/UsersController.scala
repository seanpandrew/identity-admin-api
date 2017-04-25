package controllers

import javax.inject.{Inject, Singleton}

import actions.AuthenticatedAction
import com.gu.identity.util.Logging
import com.gu.tip.Tip
import configuration.Config
import models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import services._

import scala.concurrent.Future
import scalaz.EitherT
import scalaz.std.scalaFuture._

class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class UsersController @Inject() (
    userService: UserService,
    auth: AuthenticatedAction,
    salesforce: SalesforceService,
    discussionService: DiscussionService) extends Controller with Logging {

  private val MinimumQueryLength = 2

  def search(query: String, limit: Option[Int], offset: Option[Int]) = auth.async { request =>
    ApiResponse {
      if (offset.getOrElse(0) < 0) {
        ApiResponse.Left(ApiErrors.badRequest("offset must be a positive integer"))
      }
      else if (limit.getOrElse(0) < 0) {
        ApiResponse.Left(ApiErrors.badRequest("limit must be a positive integer"))
      }
      else if (query.length < MinimumQueryLength) {
        ApiResponse.Left(ApiErrors.badRequest(s"query must be a minimum of $MinimumQueryLength characters"))
      }
      else {
        userService.search(query, limit, offset)
      }
    }
  }

  private def UserAction(userId: String) = new ActionRefiner[Request, UserRequest] {
    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {

      val subscriptionF = salesforce.getSubscriptionByIdentityId(userId)
      val membershipF = salesforce.getMembershipByIdentityId(userId)
      val hasCommentedF = discussionService.hasCommented(userId)

      for {
        user <- userService.findById(userId).asFuture
        subscription <- subscriptionF
        membership <- membershipF
        hasCommented <- hasCommentedF
      } yield {
        user match {
          case Left(r) => Left(ApiError.apiErrorToResult(r))
          case Right(r) =>
            if (Config.stage == "PROD") Tip.verify("User Retrieval")
            val userWithSubscriptions = r.copy(
              subscriptionDetails = subscription, membershipDetails = membership, hasCommented = hasCommented)
            Right(new UserRequest(userWithSubscriptions, input))
        }
      }
    }
  }

  def findById(id: String) = (auth andThen UserAction(id)) { request =>
    request.user
  }

  def update(id: String) = (auth andThen UserAction(id)).async(parse.json) { request =>
    ApiResponse {
      request.body.validate[UserUpdateRequest] match {
        case JsSuccess(result, path) =>
          UserUpdateRequestValidator.isValid(result) match {
            case Right(validUserUpdateRequest) => {
              if (Config.stage == "PROD") Tip.verify("User Update")
              logger.info(s"Updating user id:$id, body: $result")
              userService.update(request.user, validUserUpdateRequest)
            }
            case Left(e) => ApiResponse.Left(ApiErrors.badRequest(e.message))
          }
        case JsError(e) => 
          ApiResponse.Left(ApiErrors.badRequest(e.toString()))
      }
    }
  }

  def delete(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Deleting user $id")

    def unsubscribeEmails() = EitherT(ExactTargetService.unsubscribeFromAllLists(request.user.email)).leftMap(error => ApiErrors.internalError(error.toString))
    def deleteAccount() = EitherT.fromEither(userService.delete(request.user).asFuture)

    (for {
      _ <- unsubscribeEmails()
      _ <- deleteAccount()
    } yield EmailService.sendDeletionConfirmation(request.user.email)).fold(
      error => {
        logger.error(s"Error deleting user $id: $error")
        ApiError.apiErrorToResult(error)
      },
      _ => {
        logger.info(s"Successfuly deleted user $id")
        NoContent
      }
    )
  }
  
  def sendEmailValidation(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Sending email validation for user with id: $id")
    userService.sendEmailValidation(request.user).asFuture.map {
      case Right(r) => NoContent
      case Left(r) => ApiError.apiErrorToResult(r)
    }
  }

  def validateEmail(id: String) = (auth andThen UserAction(id)).async { request =>
    logger.info(s"Validating email for user with id: $id")
    userService.validateEmail(request.user).asFuture.map {
      case Right(r) => NoContent
      case Left(r) => ApiError.apiErrorToResult(r)
    }
  }
}
