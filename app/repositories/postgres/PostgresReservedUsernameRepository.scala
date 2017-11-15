package repositories.postgres

import com.google.inject.Inject
import com.gu.identity.util.Logging
import models._
import play.api.libs.json.Json
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

class PostgresReservedUsernameRepository @Inject()(implicit ec: ExecutionContext) extends Logging with PostgresJsonFormats {

  def loadReservedUsernames: ApiResponse[ReservedUsernameList] = Future {
    \/.fromTryCatchNonFatal {
      val sql = sql"SELECT jdoc from reservedusernames"
      val results = DB.readOnly { implicit session =>
        sql.map(rs => Json.parse(rs.string(1)).as[ReservedUsername]).list().apply()
      }
      ReservedUsernameList(results.map(_.username))
    }.leftMap { e =>
      val msg = "Failed to load reserved usernames"
      logger.error(msg, e)
      ApiError(msg, e.getMessage)
    }
  }

}
