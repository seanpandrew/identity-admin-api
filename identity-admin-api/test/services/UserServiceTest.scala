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
  val identityApiClient = mock[IdentityApiClient]
  val service = spy(new UserService(userReadRepo, userWriteRepo, reservedUsernameRepo, identityApiClient))

  before {
    Mockito.reset(userReadRepo, userWriteRepo, reservedUsernameRepo, identityApiClient, service)
  }

  "update" should {
    "update when email and username are valid" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = "changedEmail@theguardian.com", username = "username")
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))
      doNothing().when(service).userEmailValidation(user, updateRequest)

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
      verify(service).userEmailValidation(user, updateRequest)
    }

    "return bad request api error if the username is less than 6 chars" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = user.email, username = "123")

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Username is invalid"))
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the username is more than 20 chars" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = user.email, username = "123456789012345678901")

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Username is invalid"))
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the username is contains non alpha-numeric chars" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = user.email, username = "abc123$")

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Username is invalid"))
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the email is invalid" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = "invalid", username = "username")

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(Some(user)))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Email is invalid"))
      verifyZeroInteractions(userWriteRepo)
    }

    "return bad request api error if the email and username are invalid" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = "invalid", username = "123")

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(Some(user)))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Email and username are invalid"))
      verifyZeroInteractions(userWriteRepo)
    }

    "return internal server api error if an error occurs updating the user" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = "test@theguardian.com", username = "username")

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
      verify(userWriteRepo, never()).invalidateEmail(user.id)
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

  "userEmailValidation" should {
    "invalidate email and send validation to user when email has changed" in {
      val user = User("id", "email", username = Some("username"))
      val updateRequest = UserUpdateRequest("newEmail", "username")

      doReturn(Right(true)).when(userWriteRepo).invalidateEmail(user.id)
      doReturn(Future.successful(Right(true))).when(identityApiClient).sendEmailValidation(user.id)
      service.userEmailValidation(user, updateRequest)

      verify(userWriteRepo).invalidateEmail(user.id)
      verify(identityApiClient).sendEmailValidation(user.id)
    }

    "do nothing when email has not changed" in {
      val user = User("id", "email", username = Some("username"))
      val updateRequest = UserUpdateRequest("email", "username")

      service.userEmailValidation(user, updateRequest)

      verifyZeroInteractions(userWriteRepo)
      verifyZeroInteractions(identityApiClient)
    }
  }

}
