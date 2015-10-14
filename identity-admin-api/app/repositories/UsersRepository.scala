package repositories

import javax.inject.Inject

import models.{MongoJsFormats, User}
import models.User._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._


class UsersRepository @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends ReactiveMongoComponents {
  
  private def collection = reactiveMongoApi.db.collection[JSONCollection]("users")
  
  def createUser(user: User): Future[User] = {
    val id = BSONObjectID.generate.toString()
    val userWithId = user.copy(id)
      collection.insert[User](userWithId).map(_ => userWithId)
  }

  def findByEmail(email: String): Future[Option[User]] =  {
    collection
      .find(Json.obj("primaryEmailAddress" -> email))
      .cursor[User](ReadPreference.primaryPreferred)
      .headOption
  }

}
