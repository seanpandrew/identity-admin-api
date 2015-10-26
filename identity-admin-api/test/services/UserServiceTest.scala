package services

import com.gu.identity.model.ReservedUsernameList
import models.{ApiErrors, UserUpdateRequest, User}
import org.mockito.Mockito
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar
import repositories.{ReservedUserNameWriteRepository, UsersWriteRepository, UsersReadRepository}

import scala.concurrent.{Await, Future}
import org.mockito.Mockito._
import scala.concurrent.duration._

class UserServiceTest extends WordSpec with MockitoSugar with Matchers with BeforeAndAfter {

  val userReadRepo = mock[UsersReadRepository]
  val userWriteRepo = mock[UsersWriteRepository]
  val reservedUsernameRepo = mock[ReservedUserNameWriteRepository]
  val service = new UserService(userReadRepo, userWriteRepo, reservedUsernameRepo)

  before {
    Mockito.reset(userReadRepo, userWriteRepo, reservedUsernameRepo)
  }

  "update" should {
    "update email address when the email has changed" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest("changedEmail")
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
    }

    "return the user as is if the email has not changed" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest(user.email)

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(user)
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the email is already in use" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest("changedEmail")

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(Some(user)))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Email is in use"))
      verifyZeroInteractions(userWriteRepo)
    }

    "return internal server api error if an error occurs updating the user" in {
      val user = User("id", "email")
      val updateRequest = UserUpdateRequest("changedEmail")

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
    }
  }

  "delete" should {
    "remove the given user and reserve username" in {
      val username = "testuser"
      val user = User("id", "email", username = Some(username))
      when(userWriteRepo.delete(user)).thenReturn(Right(true))
      when(reservedUsernameRepo.addReservedUsername(username)).thenReturn(Right(ReservedUsernameList(List(username))))
      val result = service.delete(user)

      Await.result(result.underlying, 1.second) shouldEqual Right(ReservedUsernameList(List(username)))
    }

    "remove the given user and return existing reserved usernames when user has no username" in {
      val user = User("id", "email", username = None)
      when(userWriteRepo.delete(user)).thenReturn(Right(true))
      when(reservedUsernameRepo.loadReservedUsernames).thenReturn(Right(ReservedUsernameList(Nil)))
      val result = service.delete(user)

      Await.result(result.underlying, 1.second) shouldEqual Right(ReservedUsernameList(Nil))
    }

    "return internal server api error if an error occurs deleting the user" in {
      val user = User("id", "email")
      when(userWriteRepo.delete(user)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.delete(user)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
    }
  }

}
