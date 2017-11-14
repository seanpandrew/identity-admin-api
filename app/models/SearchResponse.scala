package models

import cats.Eq
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import repositories.PersistedUser

import scala.language.implicitConversions

case class SearchResponse(total: Int,
                          hasMore: Boolean,
                          results: Seq[UserSummary] = Nil)

object SearchResponse {
  implicit val format = Json.format[SearchResponse]
  
  implicit def searchResponseToResult(searchResponse: SearchResponse): Result =
    Results.Ok(Json.toJson(searchResponse))

  implicit val eq: Eq[SearchResponse] = new Eq[SearchResponse] {
    override def eqv(x: SearchResponse, y: SearchResponse) = x == y
  }

  def create(total: Int, offset: Int, results: Seq[PersistedUser]): SearchResponse = {
    val hasMore = (offset + results.size) < total
    SearchResponse(total, hasMore, results.map(UserSummary.fromPersistedUser))
  }
}
