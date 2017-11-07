package support

import java.nio.file.Paths
import de.flapdoodle.embed.process.config.IRuntimeConfig
import org.scalatest.{BeforeAndAfterAll, Suite}
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres
import ru.yandex.qatools.embed.postgresql.distribution.Version
import scalikejdbc._
import scala.collection.JavaConverters._

trait EmbeddedPostgresSupport extends BeforeAndAfterAll {
  self: Suite =>

  lazy val postgres = new EmbeddedPostgres(Version.V9_6_3)
  lazy val connectionPool = ConnectionPool.get("postgres")

  private lazy val embeddedPostgresConfig: IRuntimeConfig =
    EmbeddedPostgres.cachedRuntimeConfig(Paths.get(System.getProperty("user.home"), ".embedpostgresql"))

  def startPostgres(): String =
    postgres.start(embeddedPostgresConfig, "localhost", 5431, "identity", "username", "password", List.empty.asJava)

  def stopPostgres(): Unit = postgres.stop()

  def createTables(tableNames: String*): Unit = using(ConnectionPool.borrow("postgres")) {DB(_).localTx { implicit s =>
    tableNames.foreach { tableToCreate =>
      val create =
      s"""
           |DROP TABLE IF EXISTS $tableToCreate;
           |CREATE TABLE $tableToCreate(
           |  id VARCHAR(36) NOT NULL PRIMARY KEY,
           |  jdoc jsonb NOT NULL
           |);
           |
           |CREATE INDEX idx_$tableToCreate ON $tableToCreate USING GIN (jdoc);
         """.stripMargin
      SQL(create).update().apply()
    }
  }}

  override def beforeAll: Unit = {
    super.beforeAll()
    val url = startPostgres()
    ConnectionPool.add("postgres", url, "username", "password")
    createTables("users", "reservedusernames", "reservedemails")
  }

  override def afterAll: Unit = {
    stopPostgres()
    super.afterAll()
  }

  def execSql(sql: SQL[Nothing, NoExtractor]): Int =
    using(connectionPool.borrow()) { DB(_).localTx { implicit session =>
      sql.update().apply()
    }}

  def select[T](sql: SQL[T, HasExtractor]): Option[T] =
    using(connectionPool.borrow()) { DB(_).localTx { implicit session =>
      sql.single().apply()
    }}

}
