package actions

import javax.inject.{Inject, Singleton}

import com.gu.tip.Tip
import configuration.Config
import models.User
import play.api.mvc.{ActionRefiner, Request, Result, WrappedRequest}
import play.api.mvc.Results._
import services.{ExactTargetService, SalesforceService, UserService}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.std.scalaFuture._
import scalaz.{-\/, EitherT, \/, \/-}

class UserRequest[A](val user: User, request: Request[A]) extends WrappedRequest[A](request)

@Singleton class IdentityUserAction @Inject() (userService: UserService)(implicit ec: ExecutionContext) {

  def apply(userId: String) = new ActionRefiner[Request, UserRequest] {
    def executionContext = ec

    override def refine[A](request: Request[A]): Future[Either[Result, UserRequest[A]]] = {

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
        new UserRequest(userWithProducts, request)
      }).run.map(_.toEither)

    }
  }

}

@Singleton class OrphanUserAction @Inject() (
    salesforce: SalesforceService,
    exactTargetService: ExactTargetService)(implicit ec: ExecutionContext)  {

  def apply(email: String) = new ActionRefiner[Request, UserRequest] {
    def executionContext = ec

    override def refine[A](input: Request[A]): Future[Either[Result, UserRequest[A]]] = {
      val subOrphanOptF = EitherT(salesforce.getSubscriptionByEmail(email))
      val exactTargetOptF = EitherT(exactTargetService.subscriberByEmail(email))
      val contributionsF = EitherT(exactTargetService.contributionsByEmail(email))

      val orphanEitherT =
        for {
          subOrphanOpt <- subOrphanOptF
          exactTargetOpt <- exactTargetOptF
          contributions <- contributionsF
        } yield {

          val userRequest = Some(new UserRequest(User(
            orphan = true,
            id = "orphan",
            email = email,
            subscriptionDetails = subOrphanOpt,
            exactTargetSubscriber = exactTargetOpt,
            contributions = contributions
          ), input))

          if (subOrphanOpt.isDefined || exactTargetOpt.isDefined || !contributions.isEmpty)
            userRequest
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

}



