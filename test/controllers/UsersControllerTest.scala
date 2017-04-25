package controllers

import actions.AuthenticatedAction
import mockws.MockWS
import models._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.{Action, Request, Result}
import play.api.mvc.Results._
import play.api.test.FakeRequest
import repositories.PersistedUser
import play.api.test.Helpers._
import services.{DiscussionService, SalesforceService, UserService}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class UsersControllerTest extends WordSpec with Matchers with MockitoSugar {

  val userService = mock[UserService]
  val dapiWsMockurl = s"/profile/10000001/stats"
  val dapiWsMock = MockWS { case (GET, dapiWsMockurl) => Action {Ok("""{"status":"ok","comments":0,"pickedComments":0}""")}}

  class StubAuthenticatedAction extends AuthenticatedAction {
    val secret = "secret"
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      block(request)
    }
  }

  class StubSalesfroce extends SalesforceService {
    override def getSubscriptionByIdentityId(id: String): Future[Option[SubscriptionDetails]] = Future(None)
    override def getMembershipByIdentityId(id: String): Future[Option[MembershipDetails]] = Future(None)
  }

  val controller = new UsersController(userService, new StubAuthenticatedAction, new StubSalesfroce, new DiscussionService(dapiWsMock))

  "search" should {
    "return 400 when query string is less than minimum length" in {
      val query = "a"
      val limit = Some(10)
      val offset = Some(0)
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.badRequest("query must be a minimum of 2 characters"))
    }

    "return 400 when offset is negative" in {
      val query = "ab"
      val limit = Some(10)
      val offset = Some(-1)
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.badRequest("offset must be a positive integer"))
    }

    "return 400 when limit is negative" in {
      val query = "ab"
      val limit = Some(-10)
      val offset = Some(0)
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.badRequest("limit must be a positive integer"))
    }

    "return 200 and empty list when user not found" in {
      val query = "test@test.com"
      val limit = Some(10)
      val offset = Some(0)
      val response = SearchResponse(0, hasMore = false, Nil)
      when(userService.search(query, limit, offset)).thenReturn(ApiResponse.Right(response))
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(response)
    }
    
    "return 200 with user list as json when found" in {
      val email = "test@test.com"
      val query = email
      val user = PersistedUser(email)
      val limit = Some(10)
      val offset = Some(0)
      val response = SearchResponse(10, hasMore = true, Seq(UserSummary.fromUser(user)))
      when(userService.search(query, limit, offset)).thenReturn(ApiResponse.Right(response))
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(response)
    }
    
  }

  "findById" should {
    "return 404 when user not found" in {
      val id = "abc"
      when(userService.findById(id)).thenReturn(ApiResponse.Left[User](ApiErrors.notFound))
      val result = controller.findById(id)(FakeRequest())
      status(result) shouldEqual NOT_FOUND
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.notFound)
    }

    "return 200 when user found" in {
      val id = "abc"
      val user = User(id, "test@test.com")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      val result = controller.findById(id)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(user)
    }
  }

  "update" should {
    "return 400 when json is invalid" in {
      val id = "abc"
      val json = """{"key":"value"}"""
      val result = controller.update(id)(FakeRequest().withBody(Json.parse(json)))
      status(result) shouldEqual BAD_REQUEST
    }

    "return 404 when user is not found" in {
      val id = "abc"
      val userUpdateRequest = UserUpdateRequest(email = "test@test.com", username = Some("username"))
      when(userService.findById(id)).thenReturn(ApiResponse.Left[User](ApiErrors.notFound))
      val result = controller.update(id)(FakeRequest().withBody(Json.toJson(userUpdateRequest)))
      status(result) shouldEqual NOT_FOUND
    }

    "return 400 when username and display name differ in request" in {
      val id = "abc"
      val userUpdateRequest = UserUpdateRequest(email = "test@test.com", username = Some("username"), displayName = Some("displayname"))
      val user = User("id", "email")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.update(user, userUpdateRequest)).thenReturn(ApiResponse.Right(user))
      val result = controller.update(id)(FakeRequest().withBody(Json.toJson(userUpdateRequest)))
      status(result) shouldEqual BAD_REQUEST
    }

    "return 200 with updated user when update is successful" in {
      val id = "abc"
      val userUpdateRequest = UserUpdateRequest(email = "test@test.com", username = Some("username"))
      val user = User("id", "email")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.update(user, userUpdateRequest)).thenReturn(ApiResponse.Right(user))
      val result = controller.update(id)(FakeRequest().withBody(Json.toJson(userUpdateRequest)))
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(user)
    }
  }

  "delete" should {
    "return 404 when user is not found" in {
      val id = "abc"
      when(userService.findById(id)).thenReturn(ApiResponse.Left[User](ApiErrors.notFound))
      val result = controller.delete(id)(FakeRequest())
      status(result) shouldEqual NOT_FOUND
    }

    "return 204 when user is deleted" in {
      val id = "abc"
      val user = User("", "")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.delete(user)).thenReturn(ApiResponse.Right(ReservedUsernameList(Nil)))
      val result = controller.delete(id)(FakeRequest())
      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when error occurs" in {
      val id = "abc"
      val user = User("", "")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.delete(user)).thenReturn(ApiResponse.Left[ReservedUsernameList](ApiErrors.internalError("boom")))
      val result = controller.delete(id)(FakeRequest())
      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.internalError("boom"))
    }
  }
  
  "sendEmailValidation" should {
    "return 404 when user is not found" in {
      val id = "abc"
      when(userService.findById(id)).thenReturn(ApiResponse.Left[User](ApiErrors.notFound))
      val result = controller.sendEmailValidation(id)(FakeRequest())
      status(result) shouldEqual NOT_FOUND
    }

    "return 204 when email validation is sent" in {
      val id = "abc"
      val user = User("", "")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.sendEmailValidation(user)).thenReturn(ApiResponse.Right(true))
      val result = controller.sendEmailValidation(id)(FakeRequest())
      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when error occurs" in {
      val id = "abc"
      val user = User("", "")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.sendEmailValidation(user)).thenReturn(ApiResponse.Left[Boolean](ApiErrors.internalError("boom")))
      val result = controller.sendEmailValidation(id)(FakeRequest())
      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.internalError("boom"))
    }
  }

  "validateEmail" should {
    "return 404 when user is not found" in {
      val id = "abc"
      when(userService.findById(id)).thenReturn(ApiResponse.Left[User](ApiErrors.notFound))
      val result = controller.validateEmail(id)(FakeRequest())
      status(result) shouldEqual NOT_FOUND
    }

    "return 204 when email is validated" in {
      val id = "abc"
      val user = User("", "")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.validateEmail(user)).thenReturn(ApiResponse.Right(true))
      val result = controller.validateEmail(id)(FakeRequest())
      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when error occurs" in {
      val id = "abc"
      val user = User("", "")
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      when(userService.validateEmail(user)).thenReturn(ApiResponse.Left[Boolean](ApiErrors.internalError("boom")))
      val result = controller.validateEmail(id)(FakeRequest())
      status(result) shouldEqual INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.internalError("boom"))
    }
  }
}
