package repositories

import com.google.inject.{Inject, Singleton}
import models.{ApiError, ApiResponse, SearchResponse}
import play.api.libs.json.Json
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scalaz.{-\/, \/-}

@Singleton class PostgresDeletedUserRepository @Inject()(
  connectionPool: ConnectionPool)(implicit ec: ExecutionContext) {

  def findBy(query: String): ApiResponse[Option[DeletedUser]] = Future {
    val idMatcher = s"""{"_id":"${query.toLowerCase}"}"""
    val usernameMatcher = s"""{"username":"$query"}"""
    val emailMatcher = s"""{"email":"$query"}"""
    val sqlQuery =
      sql"""
           | SELECT jdoc FROM reservedemails
           | WHERE jdoc@>$idMatcher::jsonb
           | OR jdoc@>$emailMatcher::jsonb
           | OR jdoc@>$usernameMatcher::jsonb
       """.stripMargin
    \/-(
      using(connectionPool.borrow()) { DB(_).readOnly { implicit session =>
        sqlQuery.map(_.string(1)).single.apply.map(
          Json.parse(_).as[DeletedUser]
        )}
      }
    )
  }.recover {
    case NonFatal(e) => -\/(ApiError(e.getMessage))
  }

  def search(query: String): ApiResponse[SearchResponse] = findBy(query).map {
    case \/-(Some(user)) =>
      \/-(SearchResponse.create(1, 0, List(IdentityUser(user.email, user.id))))
    case _ =>
      \/-(SearchResponse.create(0, 0, Nil))
  }
}
