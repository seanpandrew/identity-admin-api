package repositories

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import models.SearchResponse
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection._
import reactivemongo.play.json._
import reactivemongo.api.ReadPreference

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scalaz.OptionT
import scalaz.std.scalaFuture._

import DeletedUser._

@Singleton
class DeletedUsersRepository @Inject()(reactiveMongoApi: ReactiveMongoApi) extends Logging {

  private lazy val reservedEmailsF = reactiveMongoApi.database.map(_.collection("reservedEmails"))

  def findBy(query: String): Future[Option[DeletedUser]] =
    reservedEmailsF.flatMap {
      _.find(buildSearchQuery(query))
       .cursor[DeletedUser](ReadPreference.primaryPreferred)
       .headOption
    }

  def search(query: String): Future[SearchResponse] =
    OptionT(findBy(query)).fold(
      user => SearchResponse.create(1, 0, List(PersistedUser(user.email, _id = Some(user.id)))),
      SearchResponse.create(0, 0, Nil)
    )

  def insert(id: String, email: String, username: String) =
    reservedEmailsF.flatMap(_.insert[DeletedUser](DeletedUser(id, email, username)))

  private def buildSearchQuery(query: String) =
    Json.obj(
      "$or" -> Json.arr(
        Json.obj("_id" -> query.toLowerCase),
        Json.obj("email" -> query.toLowerCase),
        Json.obj("username" -> query)
      )
    )
}
