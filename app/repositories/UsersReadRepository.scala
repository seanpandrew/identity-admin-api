package repositories

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import models.{SearchResponse, User}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api.{QueryOpts, ReadPreference}

import scala.concurrent.Future


@Singleton
class UsersReadRepository @Inject() (app: play.api.Application) extends Logging {

  private val MaximumResults = 20

  lazy val jsonCollection = app.injector.instanceOf[ReactiveMongoApi].db.collection[JSONCollection]("users")

  def search(query: String, limit: Option[Int] = None, offset: Option[Int] = None): Future[SearchResponse] =  {
    val q = buildSearchQuery(query)
    val total = jsonCollection.count(Some(q))

    val l = limit.getOrElse(MaximumResults)
    val o = offset.getOrElse(0)

    val results = jsonCollection
      .find(q)
      .options(QueryOpts(o, l))
      .cursor[PersistedUser](ReadPreference.primaryPreferred)
      .collect[List](l)

    for {
      t <- total
      r <- results
    } yield {
      SearchResponse.create(t, o, r)
    }
  }

  private def buildSearchQuery(query: String): JsObject = {
    val term = query.toLowerCase
    Json.obj(
      "$or" -> Json.arr(
        Json.obj("_id" -> term),
        Json.obj("searchFields.emailAddress" -> term),
        Json.obj("searchFields.username" -> term),
        Json.obj("searchFields.postcode" -> term),
        Json.obj("searchFields.postcodePrefix" -> term),
        Json.obj("searchFields.displayName" -> term),
        Json.obj("privateFields.registrationIp" -> term),
        Json.obj("privateFields.lastActiveIpAddress" -> term)
      )
    )
  }
  
  private def findBy(field: String, value: String): Future[Option[User]] =
    jsonCollection
      .find(Json.obj(field -> value))
      .cursor[PersistedUser](ReadPreference.primaryPreferred)
      .headOption.map(_.map(User.fromPersistedUser))

  def findById(id: String): Future[Option[User]] = findBy("_id", id)
  
  def findByEmail(email: String): Future[Option[User]] = findBy("primaryEmailAddress", email.toLowerCase)
  def findByUsername(username: String): Future[Option[User]] = findBy("publicFields.usernameLowerCase", username.toLowerCase)
  def findByVanityUrl(vanityUrl: String): Future[Option[User]] = findBy("publicFields.vanityUrl", vanityUrl)

  def count(): Future[Int] = jsonCollection.count()
}