package services

import models.{ApiErrors, UserUpdateRequest, User}
import org.mockito.Mockito
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar
import repositories.{UsersWriteRepository, UsersReadRepository}

import scala.concurrent.{Await, Future}
import org.mockito.Mockito._
import scala.concurrent.duration._

class UserServiceTest extends WordSpec with MockitoSugar with Matchers with BeforeAndAfter {

  val readRepo = mock[UsersReadRepository]
  val writeRepo = mock[UsersWriteRepository]
  val service = new UserService(readRepo, writeRepo)

  before {
    Mockito.reset(readRepo, writeRepo)
  }

  "update" should {
    "update email address when the email has changed" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest("changedEmail")
      val updatedUser = user.copy(email = updateRequest.email)

      when(readRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(writeRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
    }

    "return the user as is if the email has not changed" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest(user.email)

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(user)
      verifyZeroInteractions(readRepo, writeRepo)
    }

    "return bad request api error if the email is already in use" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest("changedEmail")

      when(readRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(Some(user)))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Email is in use"))
      verifyZeroInteractions(writeRepo)
    }

    "return internal server api error if an error occurs updating the user" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest("changedEmail")

      when(readRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(writeRepo.update(user, updateRequest)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
    }
  }

  "delete" should {
    "remove the given user" in {
      val user = User("id", "email")
      when(writeRepo.delete(user)).thenReturn(Right(true))
      val result = service.delete(user)

      Await.result(result.underlying, 1.second) shouldEqual Right(true)
    }

    "return internal server api error if an error occurs deleting the user" in {
      val user = User("id", "email")
      when(writeRepo.delete(user)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.delete(user)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
    }
  }

}
