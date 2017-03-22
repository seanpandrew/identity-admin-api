package repositories

import javax.inject.Inject

import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import de.flapdoodle.embed.mongo.distribution.Version
import org.scalatest.{BeforeAndAfterAll, Suites}
import play.api.Application

class RepositoryTest @Inject() (app: Application) extends Suites(new UsersRepositoryTest(app), new ReservedUsernameRepositoryTest) with MongoEmbedDatabase with BeforeAndAfterAll {

  var mongoProps: MongodProps = null

  override def beforeAll() { mongoProps = mongoStart(port = 12345, version = Version.V2_4_10) }

  override def afterAll() { mongoStop(mongoProps) }

}
