package repositories

import javax.inject.Inject

import models.{SearchResponse, User}
import models.User._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._

import scala.concurrent.Future

import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.{QueryOpts, ReadPreference}

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._


class UsersReadRepository @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends ReactiveMongoComponents {

  private val MaximumResults = 20

  def jsonCollection = reactiveMongoApi.db.collection[JSONCollection]("users")

  def search(query: String, limit: Option[Int] = None, offset: Option[Int] = None): Future[SearchResponse] =  {
    val q = buildSearchQuery(query)
    val total = jsonCollection.count(Some(q))

    val l = limit.getOrElse(MaximumResults)
    val o = offset.getOrElse(0)

    val results = jsonCollection
      .find(q)
      .options(QueryOpts(o, l))
      .cursor[User](ReadPreference.primaryPreferred)
      .collect[List](l)

    for {
      t <- total
      r <- results
    } yield {
      SearchResponse.create(t, o, r)
    }
  }

  private def buildSearchQuery(query: String): JsObject =
    Json.obj(
      "$or" -> Json.arr(
        Json.obj("primaryEmailAddress" -> query),
        Json.obj("publicFields.username" -> query),
        Json.obj("privateFields.postcode" -> query)
      )
    )

  def findById(id: String): Future[Option[User]] =
    jsonCollection
    .find(Json.obj("_id" -> id))
    .cursor[User](ReadPreference.primaryPreferred)
    .headOption
}
