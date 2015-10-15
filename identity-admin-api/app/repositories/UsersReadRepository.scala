package repositories

import javax.inject.Inject

import models.User
import models.User._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONObjectID}

import scala.concurrent.Future

import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._


class UsersReadRepository @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends ReactiveMongoComponents {

  def jsonCollection = reactiveMongoApi.db.collection[JSONCollection]("users")
  def bsonCollection = reactiveMongoApi.db.collection[BSONCollection]("users")

  def createUser(email: String): Future[String] = {
    val id = BSONObjectID.generate.toString()
    reactiveMongoApi.db("users").insert(BSONDocument("_id" -> id, "primaryEmailAddress" -> email)).map(_ => id)
  }

  def findByEmail(email: String): Future[Seq[User]] =  {
    jsonCollection
      .find(Json.obj("primaryEmailAddress" -> email))
      .cursor[User](ReadPreference.primaryPreferred)
      .collect[List]()
  }

}
