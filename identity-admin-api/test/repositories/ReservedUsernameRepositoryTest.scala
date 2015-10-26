package repositories

import java.util.UUID

import com.gu.identity.model.ReservedUsernameList
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
      result mustEqual Right(ReservedUsernameList(List(username)))
    }
  }
}
