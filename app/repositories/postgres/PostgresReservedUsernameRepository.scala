package repositories.postgres

import com.google.inject.Inject
import com.gu.identity.util.Logging
import models._
import play.api.libs.json.Json
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

class PostgresReservedUsernameRepository @Inject()(implicit ec: ExecutionContext) extends Logging
  with PostgresJsonFormats
  with PostgresUtils {

  def loadReservedUsernames: ApiResponse[ReservedUsernameList] = readOnly { implicit session =>
    val sql = sql"SELECT jdoc from reservedusernames"
    val results = sql.map(rs => Json.parse(rs.string(1)).as[ReservedUsername]).list().apply()
    ReservedUsernameList(results.map(_.username))
  }(logFailure("Failed to load reserved usernames"))

}
