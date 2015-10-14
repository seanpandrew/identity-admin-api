package repositories

import java.util.UUID

import models.User
import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Play
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Await
import scala.concurrent.duration._

class UsersRepositoryTest extends PlaySpec with OneServerPerSuite with Eventually {

    "findUserByEmail" must {
      "return a user when email is found" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val email = s"${UUID.randomUUID().toString}@test.com"
        val user = User(email, Some(BSONObjectID.generate.toString()))
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.findByEmail(email), 1.second).flatMap(_._id) mustEqual createdUser
      }
      
      "return None when email is not found" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        Await.result(repo.findByEmail("invalid@invalid.com"), 1.second) mustEqual None
      }

  }

}
