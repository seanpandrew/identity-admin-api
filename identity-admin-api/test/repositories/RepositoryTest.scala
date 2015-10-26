package repositories

import com.github.simplyscala.{MongodProps, MongoEmbedDatabase}
import de.flapdoodle.embed.mongo.distribution.Version
import org.scalatest.{BeforeAndAfterAll, Suites}

class RepositoryTest extends Suites(new UsersRepositoryTest, new ReservedUsernameRepositoryTest) with MongoEmbedDatabase with BeforeAndAfterAll {

  var mongoProps: MongodProps = null

  override def beforeAll() { mongoProps = mongoStart(port = 12345, version = Version.V2_4_10) }

  override def afterAll() { mongoStop(mongoProps) }

}
