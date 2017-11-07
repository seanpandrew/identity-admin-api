package repositories

import models.{ApiResponse, SearchResponse}
import play.api.libs.json.Json
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/-

class PostgresDeletedUserRepository(connectionPool: ConnectionPool)(implicit ec: ExecutionContext) {

  def search(query: String): ApiResponse[SearchResponse] = Future {
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

    val optionalUser = using(connectionPool.borrow()){ DB(_).readOnly { implicit s =>
      sqlQuery.map(rs => rs.string(1)).single().apply().map(jsonString =>
        Json.parse(jsonString).as[DeletedUser]
      )
    }}
    optionalUser match {
      case None => \/-(SearchResponse.create(0, 0, Nil))
      case Some(user) => \/-(SearchResponse.create(1, 0, List(IdentityUser(user.email, user.id))))
    }
  }
}
