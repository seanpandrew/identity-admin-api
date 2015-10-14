package repositories

import java.util.UUID

import models.User
import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Play

import scala.concurrent.Await
import scala.concurrent.duration._

class UsersRepositoryTest extends PlaySpec with OneServerPerSuite with Eventually {

    "findUserByEmail" must {
      "return a user when email is found" ignore {
        val repo = Play.current.injector.instanceOf(classOf[UsersRepository])
        val email = s"${UUID.randomUUID().toString}@test.com"
        val user = User(email)
        val createdUser = Await.result(repo.createUser(user), 1.second)
        Await.result(repo.findByEmail(email), 1.second) mustEqual Some(createdUser)
      }
      
      "return None when email is not found" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersRepository])
        Await.result(repo.findByEmail("invalid@invalid.com"), 1.second) mustEqual None
      }

  }

}
