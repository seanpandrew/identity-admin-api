package repositories

import java.util.UUID

import models.ApiErrors
import org.scalatest.DoNotDiscover
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Play


@DoNotDiscover
class ReservedUsernameRepositoryTest extends PlaySpec with OneServerPerSuite {

  "add reserved username" should {
    "add a given username to the list and return the updated list" in {
      val repo = Play.current.injector.instanceOf(classOf[ReservedUserNameWriteRepository])
      val username = UUID.randomUUID().toString

      val result = repo.addReservedUsername(username)
      result.isRight mustBe true
      result.right.get.reservedUsernames must contain(username)
    }
  }

  "remove reserved username" should {
    "remove a given username from the list and return the updated list" in {
      val repo = Play.current.injector.instanceOf(classOf[ReservedUserNameWriteRepository])
      val username = UUID.randomUUID().toString

      repo.addReservedUsername(username)

      val result = repo.removeReservedUsername(username)
      result.isRight mustBe true
      result.right.get.reservedUsernames must not contain(username)
    }

    "return not found if the username doesn't exist" in {
      val repo = Play.current.injector.instanceOf(classOf[ReservedUserNameWriteRepository])
      val username = UUID.randomUUID().toString

      val result = repo.removeReservedUsername(username)
      result mustEqual Left(ApiErrors.notFound)
    }
  }
}
