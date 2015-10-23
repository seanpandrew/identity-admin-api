package controllers

import actions.AuthenticatedAction
import models._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.{Result, Request}
import play.api.test.FakeRequest
import repositories.PersistedUser
import play.api.test.Helpers._
import services.UserService

import scala.concurrent.Future

class UsersControllerTest extends WordSpec with Matchers with MockitoSugar {

  val userService = mock[UserService]
  class StubAuthenticatedAction extends AuthenticatedAction {
    val secret = "secret"
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      block(request)
    }
  }
  val controller = new UsersController(userService, new StubAuthenticatedAction)
  
  "search" should {
    "return 400 when query string is less than minimum length" in {
      val query = "ab"
      val limit = Some(10)
      val offset = Some(0)
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.badRequest("query must be a minimum of 3 characters"))
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
      val userUpdateRequest = UserUpdateRequest(email = "test@test.com")
      when(userService.findById(id)).thenReturn(ApiResponse.Left[User](ApiErrors.notFound))
      val result = controller.update(id)(FakeRequest().withBody(Json.toJson(userUpdateRequest)))
      status(result) shouldEqual NOT_FOUND
    }

    "return 200 with updated user when update is successful" in {
      val id = "abc"
      val userUpdateRequest = UserUpdateRequest(email = "test@test.com")
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

    "return 204 when user is found" in {
      val id = "abc"
      val user = mock[User]
      when(userService.findById(id)).thenReturn(ApiResponse.Right(user))
      val result = controller.delete(id)(FakeRequest())
      status(result) shouldEqual NO_CONTENT
    }
  }
}
