package repositories

import java.util.UUID

import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import de.flapdoodle.embed.mongo.distribution.Version
import models.User
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Play
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Await
import scala.concurrent.duration._

class UsersRepositoryTest extends PlaySpec with OneServerPerSuite with Eventually with MongoEmbedDatabase with BeforeAndAfterAll {

  var mongoProps: MongodProps = null

  override def beforeAll() { mongoProps = mongoStart(port = 12345, version = Version.V2_4_10) }

  override def afterAll() { mongoStop(mongoProps) }

    "findUserByEmail" must {
      "return a user when email matches exactly" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val email = s"${UUID.randomUUID().toString}@test.com"
        val user = User(email, Some(BSONObjectID.generate.toString()))
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.findByEmail(email), 1.second).flatMap(_._id) must contain(createdUser.get)
      }

      "return a user when email partially matches end" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val email = s"${UUID.randomUUID().toString}@test.com"
        val user = User(email, Some(BSONObjectID.generate.toString()))
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.findByEmail("test.com"), 1.second).flatMap(_._id) must contain(createdUser.get)
      }

      "return a user when email partially matches start" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val id = UUID.randomUUID().toString
        val email = s"$id@test.com"
        val user = User(email, Some(BSONObjectID.generate.toString()))
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.findByEmail(id), 1.second).flatMap(_._id) must contain(createdUser.get)
      }
      
      "return Nil when email is not found" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        Await.result(repo.findByEmail("invalid@invalid.com"), 1.second) mustEqual Nil
      }

  }

}
