package repositories

import java.util.UUID

import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import de.flapdoodle.embed.mongo.distribution.Version
import models.SearchResponse
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

  def createUser(username: Option[String] = None, postcode: Option[String] = None): User = {
    val email = s"${UUID.randomUUID().toString}@test.com"
    User(email,
      Some(BSONObjectID.generate.toString()),
      publicFields = Some(PublicFields(username = username)),
      privateFields = Some(PrivateFields(postcode = postcode)))
  }

    "search" must {

      "return a user when email matches exactly" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val user = createUser()
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.search(user.primaryEmailAddress), 1.second).results.map(_.id) must contain(createdUser.get)
      }

      "return a user when username matches exactly" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val username = UUID.randomUUID().toString
        val user = createUser(username = Some(username))
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.search(username), 1.second).results.map(_.id) must contain(createdUser.get)
      }

      "return a user when postcode matches exactly" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val postcode = "N1 9GU"
        val user = createUser(postcode = Some(postcode))
        val createdUser = writeRepo.createUser(user)
        Await.result(repo.search(postcode), 1.second).results.map(_.id) must contain(createdUser.get)
      }
      
      "return Nil when no results are found" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        Await.result(repo.search("invalid@invalid.com"), 1.second) mustEqual SearchResponse(0, hasMore = false, Nil)
      }

      "use offset when provided" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val postcode = UUID.randomUUID().toString
        val user1 = createUser(postcode = Some(postcode))
        val user2 = createUser(postcode = Some(postcode))
        val user3 = createUser(postcode = Some(postcode))
        val user4 = createUser(postcode = Some(postcode))
        val user5 = createUser(postcode = Some(postcode))

        val createdUser1 = writeRepo.createUser(user1)
        val createdUser2 = writeRepo.createUser(user2)
        val createdUser3 = writeRepo.createUser(user3)
        val createdUser4 = writeRepo.createUser(user4)
        val createdUser5 = writeRepo.createUser(user5)

        val ids = Await.result(repo.search(postcode, offset = Some(1)), 1.second).results.map(_.id)
        ids must not contain createdUser1.get
        ids must contain(createdUser2.get)
        ids must contain(createdUser3.get)
        ids must contain(createdUser4.get)
        ids must contain(createdUser5.get)
      }

      "use limit when provided" in {
        val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
        val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
        val postcode = UUID.randomUUID().toString
        val user1 = createUser(postcode = Some(postcode))
        val user2 = createUser(postcode = Some(postcode))
        val user3 = createUser(postcode = Some(postcode))
        val user4 = createUser(postcode = Some(postcode))
        val user5 = createUser(postcode = Some(postcode))

        val createdUser1 = writeRepo.createUser(user1)
        val createdUser2 = writeRepo.createUser(user2)
        val createdUser3 = writeRepo.createUser(user3)
        val createdUser4 = writeRepo.createUser(user4)
        val createdUser5 = writeRepo.createUser(user5)

        val ids = Await.result(repo.search(postcode, offset = Some(1), limit = Some(2)), 1.second).results.map(_.id)
        ids.size mustEqual 2
        ids must contain(createdUser2.get)
        ids must contain(createdUser3.get)
      }

  }

  "findById" should {
    "return Some user when user found" in {
      val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])
      val writeRepo = Play.current.injector.instanceOf(classOf[UsersWriteRepository])
      val user1 = createUser()
      val createdUser1 = writeRepo.createUser(user1)

      Await.result(repo.findById(createdUser1.get), 1.second).map(_.id) mustEqual createdUser1
    }

    "return None when user not found" in {
      val repo = Play.current.injector.instanceOf(classOf[UsersReadRepository])

      Await.result(repo.findById(UUID.randomUUID().toString), 1.second) mustEqual None
    }
  }

}
