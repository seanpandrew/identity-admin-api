package repositories

import javax.inject.Inject

import models.User
import models.User._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._

import scala.concurrent.Future

import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._


class UsersReadRepository @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends ReactiveMongoComponents {

  private val MaximumResults = 20

  def jsonCollection = reactiveMongoApi.db.collection[JSONCollection]("users")

  def search(query: String): Future[Seq[User]] =  {
    jsonCollection
      .find(buildSearchQuery(query))
      .cursor[User](ReadPreference.primaryPreferred)
      .collect[List](MaximumResults)
  }

  private def buildSearchQuery(query: String): JsObject =
    Json.obj(
      "$or" -> Json.arr(
        Json.obj("primaryEmailAddress" -> query),
        Json.obj("publicFields.username" -> query),
        Json.obj("privateFields.postcode" -> query)
      )
    )

}
