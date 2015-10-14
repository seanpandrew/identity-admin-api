package controllers

import models.{User, ApiErrors}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import repositories.UsersRepository
import play.api.test.Helpers._

import scala.concurrent.Future

class UsersControllerTest extends WordSpec with Matchers with MockitoSugar {

  val userRepo = mock[UsersRepository]
  val controller = new UsersController(userRepo)
  
  "findUserByEmail" should {
    "return 404 when user not found" in {
      val email = "test@test.com"
      when(userRepo.findByEmail(email)).thenReturn(Future.successful(None))
      val result = controller.findUserByEmail(email)(FakeRequest())
      status(result) shouldEqual NOT_FOUND
      contentAsJson(result) shouldEqual Json.toJson(ApiErrors.notFound)
    }
    
    "return 200 with user as json when found" in {
      val email = "test@test.com"
      val user = User(email)
      when(userRepo.findByEmail(email)).thenReturn(Future.successful(Some(user)))
      val result = controller.findUserByEmail(email)(FakeRequest())
      status(result) shouldEqual OK
      contentAsJson(result) shouldEqual Json.toJson(user)
    }
    
  }
}
