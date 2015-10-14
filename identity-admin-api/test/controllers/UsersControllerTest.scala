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
  
  "findUserByEmail" should {
    "return 200 and empty list when user not found" in {
      val email = "test@test.com"
      when(userRepo.findByEmail(email)).thenReturn(Future.successful(Nil))
      val result = controller.findUserByEmail(email)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(SearchResponse())
    }
    
    "return 200 with user list as json when found" in {
      val email = "test@test.com"
      val user = User(email)
      when(userRepo.findByEmail(email)).thenReturn(Future.successful(Seq(user)))
      val result = controller.findUserByEmail(email)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(SearchResponse(Seq(UserSummary.fromUser(user))))
    }
    
  }
}
