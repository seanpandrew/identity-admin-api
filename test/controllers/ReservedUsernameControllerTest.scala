package controllers

import actions.AuthenticatedAction
import models.{ApiErrors, ReservedUsernameList}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import play.api.mvc.{Result, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.ReservedUserNameWriteRepository
import org.mockito.Mockito._

import scala.concurrent.Future

class ReservedUsernameControllerTest extends WordSpec with Matchers with MockitoSugar {

  val reservedUsernameRepo = mock[ReservedUserNameWriteRepository]

  class StubAuthenticatedAction extends AuthenticatedAction {
    val secret = "secret"
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      block(request)
    }
  }
  val controller = new ReservedUsernameController(reservedUsernameRepo, new StubAuthenticatedAction)


  "reserveUsername" should {
    "return bad request when json cannot be parsed" in {
      val json = """{"key":"value"}"""
      val result = controller.reserveUsername()(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual BAD_REQUEST
    }

    "return no content when succesful" in {
      val json = """{"username":"usernameToReserve"}"""
      when(reservedUsernameRepo.addReservedUsername("usernameToReserve")).thenReturn(Right(ReservedUsernameList()))
      val result = controller.reserveUsername()(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual NO_CONTENT
    }

    "return internal server error when error occurs" in {
      val json = """{"username":"usernameToReserve"}"""
      val error = ApiErrors.internalError("boom")
      when(reservedUsernameRepo.addReservedUsername("usernameToReserve")).thenReturn(Left(error))
      val result = controller.reserveUsername()(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldEqual Json.toJson(error)
    }
  }

  "unreserveUsername" should {
    "return bad request when json cannot be parsed" in {
      val json = """{"key":"value"}"""
      val result = controller.unreserveUsername()(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual BAD_REQUEST
    }

    "return no content when succesful" in {
      val json = """{"username":"usernameToReserve"}"""
      when(reservedUsernameRepo.removeReservedUsername("usernameToReserve")).thenReturn(Right(ReservedUsernameList()))
      val result = controller.unreserveUsername()(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual NO_CONTENT
    }

    "return internal server error when error occurs" in {
      val json = """{"username":"usernameToReserve"}"""
      val error = ApiErrors.internalError("boom")
      when(reservedUsernameRepo.removeReservedUsername("usernameToReserve")).thenReturn(Left(error))
      val result = controller.unreserveUsername()(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldEqual Json.toJson(error)
    }
  }

  "getReservedUsernames" should {
    "return reserved usernames when successful" in {
      val reservedUsernameList = ReservedUsernameList(List("1", "2", "3"))
      when(reservedUsernameRepo.loadReservedUsernames).thenReturn(Right(reservedUsernameList))
      val result = controller.getReservedUsernames(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(reservedUsernameList)
    }

    "return internal server error when error occurs" in {
      val error = ApiErrors.internalError("boom")
      when(reservedUsernameRepo.loadReservedUsernames).thenReturn(Left(error))
      val result = controller.getReservedUsernames(FakeRequest())
      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldEqual Json.toJson(error)
    }
  }
}
