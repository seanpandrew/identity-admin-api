package controllers

import models.{UserSummary, SearchResponse, User, ApiErrors}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import repositories.UsersReadRepository
import play.api.test.Helpers._

import scala.concurrent.Future

class UsersControllerTest extends WordSpec with Matchers with MockitoSugar {

  val userRepo = mock[UsersReadRepository]
  val controller = new UsersController(userRepo)
  
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
      when(userRepo.search(query, limit, offset)).thenReturn(Future.successful(response))
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(response)
    }
    
    "return 200 with user list as json when found" in {
      val email = "test@test.com"
      val query = email
      val user = User(email)
      val limit = Some(10)
      val offset = Some(0)
      val response = SearchResponse(10, hasMore = true, Seq(UserSummary.fromUser(user)))
      when(userRepo.search(query, limit, offset)).thenReturn(Future.successful(response))
      val result = controller.search(query, limit, offset)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(response)
    }
    
  }
}
