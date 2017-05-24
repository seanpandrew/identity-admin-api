package services

import actors.EventPublishingActorProvider
import models._
import util.UserConverter._
import org.mockito.Mockito
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import org.scalatest.mockito.MockitoSugar
import repositories.{DeletedUsersRepository, IdentityUserUpdate, ReservedUserNameWriteRepository, UsersReadRepository, UsersWriteRepository}

import scala.concurrent.{Await, Future}
import org.mockito.Mockito._

import scala.concurrent.duration._

class UserServiceTest extends WordSpec with MockitoSugar with Matchers with BeforeAndAfter {

  val jobsGroup = Seq(UserGroup( "GRS", "/sys/policies/guardian-jobs"))

  val userReadRepo = mock[UsersReadRepository]
  val userWriteRepo = mock[UsersWriteRepository]
  val reservedUsernameRepo = mock[ReservedUserNameWriteRepository]
  val identityApiClient = mock[IdentityApiClient]
  val eventPublishingActorProvider = mock[EventPublishingActorProvider]
  val deletedUsersRepository = mock[DeletedUsersRepository]
  val salesforceService = mock[SalesforceService]
  val madgexService = mock[MadgexService]
  val service =
    spy(new UserService(userReadRepo, userWriteRepo, reservedUsernameRepo, identityApiClient,
      eventPublishingActorProvider, deletedUsersRepository, salesforceService, madgexService))

  before {
    Mockito.reset(userReadRepo, userWriteRepo, reservedUsernameRepo, identityApiClient, eventPublishingActorProvider, service, madgexService)
  }

  "isUsernameChanged" should {
    "return true if a username is being changed" in {
      service.isUsernameChanged(Some("changedUsername"), Some("oldUsername")) should be(true)
    }

    "return false if a username is not being changed" in {
      service.isUsernameChanged(Some("oldUsername"), Some("oldUsername")) should be(false)
    }

    "return true if a username is being added" in {
      service.isUsernameChanged(Some("newUsername"), None) should be(true)
    }

    "return false if a zero length username is being added" in {
      service.isUsernameChanged(None, None) should be(false)
      service.isUsernameChanged(None, Some("existingUsername")) should be(true)
    }
  }

  "isDisplayNameChanged" should {
    "return true if a username is being changed" in {
      service.isDisplayNameChanged(Some("changedDisplayName"), Some("oldDisplayName")) should be(true)
    }

    "return false if a username is not being changed" in {
      service.isDisplayNameChanged(Some("oldDisplayName"), Some("oldDisplayName")) should be(false)
    }

    "return true if a username is being added" in {
      service.isDisplayNameChanged(Some("changedDisplayName"), None) should be(true)
    }

    "return false if a zero length username is being added" in {
      service.isDisplayNameChanged(None, None) should be(false)
      service.isDisplayNameChanged(None, Some("existingDisplayName")) should be(true)
    }
  }

  "isJobsUser" should {
    "return true for a jobs user" in {
      val user = User("id", "email@theguardian.com", groups = jobsGroup)
      service.isJobsUser(user) should be(true)
    }

    "return true for a non-jobs user" in {
      val user = User("id", "email@theguardian.com")
      service.isJobsUser(user) should be(false)
    }
  }

  "isEmailValidationChanged" should {
    "return true if e-mail validation status is changing" in {
      service.isEmailValidationChanged(Some(false), Some(true)) should be(true)
      service.isEmailValidationChanged(Some(false), None) should be(true)
      service.isEmailValidationChanged(Some(true), Some(false)) should be(true)
      service.isEmailValidationChanged(Some(true), None) should be(true)
      service.isEmailValidationChanged(None, Some(true)) should be(true)
      service.isEmailValidationChanged(None, Some(false)) should be(true)
    }

    "return false if e-mail validation status is not changing" in {
      service.isEmailValidationChanged(None, None) should be(false)
      service.isEmailValidationChanged(Some(true), Some(true)) should be(false)
      service.isEmailValidationChanged(Some(false), Some(false)) should be(false)
    }
  }

  "update" should {
    "update when email and username are valid" in {
      val user = User("id", "email@theguardian.com")
      val userUpdateRequest = UserUpdateRequest(email = "changedEmail@theguardian.com", username = Some("username"))
      val updateRequest = IdentityUserUpdate(userUpdateRequest, Some(false))
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))
      when(identityApiClient.sendEmailValidation(user.id)).thenReturn(Future.successful(Right(true)))

      val result = service.update(user, userUpdateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
      verify(identityApiClient).sendEmailValidation(user.id)
    }

    "update madgex when jobs user changed" in {
      val user = User("id", "email@theguardian.com", groups = jobsGroup)
      val userUpdateRequest = UserUpdateRequest(email = "changedEmail@theguardian.com")
      val gNMMadgexUser = GNMMadgexUser(user.id, userUpdateRequest)
      val updateRequest = IdentityUserUpdate(userUpdateRequest, Some(false))
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))
      when(identityApiClient.sendEmailValidation(user.id)).thenReturn(Future.successful(Right(true)))
      when(madgexService.update(gNMMadgexUser)).thenReturn(Future.successful(true))

      val result = service.update(user, userUpdateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
      verify(madgexService).update(gNMMadgexUser)
    }

    "not update madgex when non-jobs user changed" in {
      val user = User("id", "email@theguardian.com")
      val userUpdateRequest = UserUpdateRequest(email = "changedEmail@theguardian.com")
      val updateRequest = IdentityUserUpdate(userUpdateRequest, Some(false))
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))

      val result = service.update(user, userUpdateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
      verifyZeroInteractions(madgexService)
    }

    "not send email validation when email has not changed" in {
      val user = User("id", "email@theguardian.com")
      val userUpdateRequest = UserUpdateRequest(email = "email@theguardian.com", username = Some("username"))
      val updateRequest = IdentityUserUpdate(userUpdateRequest, None)
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))

      val result = service.update(user, userUpdateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
      verifyZeroInteractions(identityApiClient)
    }

    "not update madgex when jobs user as not changed" in {
      val user = User("id", "email@theguardian.com")
      val userUpdateRequest = UserUpdateRequest(email = "email@theguardian.com", username = Some("username"))
      val gNMMadgexUser = GNMMadgexUser(user.id, userUpdateRequest)
      val updateRequest = IdentityUserUpdate(userUpdateRequest, None)
      val updatedUser = user.copy(email = updateRequest.email)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Right(updatedUser))

      val result = service.update(user, userUpdateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Right(updatedUser)
      verifyZeroInteractions(madgexService)
    }

    "return bad request api error if the username is less than 6 chars" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = user.email, username = Some("123"))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Username is invalid"))
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the username is more than 20 chars" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = user.email, username = Some("123456789012345678901"))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Username is invalid"))
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the username is contains non alpha-numeric chars" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = user.email, username = Some("abc123$"))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Username is invalid"))
      verifyZeroInteractions(userReadRepo, userWriteRepo)
    }

    "return bad request api error if the email is invalid" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = "invalid", username = Some("username"))

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(Some(user)))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Email is invalid"))
      verifyZeroInteractions(userWriteRepo)
    }

    "return bad request api error if the email and username are invalid" in {
      val user = User("id", "email@theguardian.com")
      val updateRequest = UserUpdateRequest(email = "invalid", username = Some("123"))

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(Some(user)))

      val result = service.update(user, updateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.badRequest("Email and username are invalid"))
      verifyZeroInteractions(userWriteRepo)
    }

    "return internal server api error if an error occurs updating the user" in {
      val user = User("id", "email@theguardian.com")
      val userUpdateRequest = UserUpdateRequest(email = "email@theguardian.com", username = Some("username"))
      val updateRequest = IdentityUserUpdate(userUpdateRequest, None)

      when(userReadRepo.findByEmail(updateRequest.email)).thenReturn(Future.successful(None))
      when(userWriteRepo.update(user, updateRequest)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.update(user, userUpdateRequest)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
      verify(identityApiClient, never()).sendEmailValidation(user.id)
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

  "validateEmailAddress" should {
    "validate the email address" in {
      val user = User("id", "email")
      when(userWriteRepo.updateEmailValidationStatus(user, true)).thenReturn(Right(user))
      val result = service.validateEmail(user)

      Await.result(result.underlying, 1.second) shouldEqual Right(true)
    }

    "return internal server api error if an error occurs validating the email address" in {
      val user = User("id", "email")
      when(userWriteRepo.updateEmailValidationStatus(user, true)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.validateEmail(user)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
    }
  }

  "sendEmailValidation" should {
    "invalidate the email address and send validation request email" in {
      val user = User("id", "email")
      when(userWriteRepo.updateEmailValidationStatus(user, false)).thenReturn(Right(user))
      when(identityApiClient.sendEmailValidation(user.id)).thenReturn(Future.successful(Right(true)))
      val result = service.sendEmailValidation(user)

      Await.result(result.underlying, 1.second) shouldEqual Right(true)
    }

    "return internal server api error if an error occurs invalidating the email address" in {
      val user = User("id", "email")
      when(userWriteRepo.updateEmailValidationStatus(user, false)).thenReturn(Left(ApiErrors.internalError("boom")))

      val result = service.sendEmailValidation(user)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
      verifyZeroInteractions(identityApiClient)
    }

    "return internal server api error if an error occurs sending the email" in {
      val user = User("id", "email")
      when(userWriteRepo.updateEmailValidationStatus(user, false)).thenReturn(Right(user))
      when(identityApiClient.sendEmailValidation(user.id)).thenReturn(Future.successful(Left(ApiErrors.internalError("boom"))))

      val result = service.sendEmailValidation(user)

      Await.result(result.underlying, 1.second) shouldEqual Left(ApiErrors.internalError("boom"))
    }
  }

}
