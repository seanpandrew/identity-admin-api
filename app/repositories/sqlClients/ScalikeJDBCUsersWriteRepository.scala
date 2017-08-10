package repositories.sqlClients

import models.{ApiError, ApiResponse, User}
import play.api.libs.json.Json
import repositories.{IdentityUser, IdentityUserUpdate, StatusFields}
import scalikejdbc._

import scala.concurrent.Future
import scalaz.{-\/, \/-}
import RepoHelpers._
import com.gu.identity.util.Logging

// TODO replace
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Assume:
  * - jdoc is json document column name in Postgres
  *
  * Helpers:
  * - jdon->'[key]' to select key
  * - jdoc @ '{"key": "value"}' to query against specific key-value match
  */

/**
  * I liked:
  * - easy to translate from docs into query as raw sql
  *
  * Not sure about:
  * - jsonb queries are harder to read because of json syntax and also related escaping
  * - errors (will be captured by Future when wrapped properly though I guess)
  * - little type safety (i.e. can write anything to jdoc, but maybe this is
  *   inevitable result of using jsonb?)
  * - not async so need to do that ourselves
  *
  * Other thoughts:
  * - I tried the DSL syntax but it assumes you're working with regular columns
  *   and doesn't support jsonb functions.
  */

/*
import repositories.sqlClients._
val client = new ScalikeJDBCUsersWriteRepository
val idUser = client.findBy("21836057")

// to test out email update
import models._
val user = User.fromIdentityUser(idUser.value.get.get)
client.updateEmailValidationStatus(user, true)

*/


class ScalikeJDBCUsersWriteRepository extends UsersWriteRepo with Logging {

  // Methods return ApiResponse[User] mostly
  // Internal methods return Future[IdentityUser] -> this is the jdoc structure

  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton("jdbc:postgresql:identity", "identity_admin", "")

  implicit val session = AutoSession

  // Key is _id or email address
  def findBy(key: String): Future[IdentityUser] = {

    // TODO make async
    val result: Option[IdentityUser] = DB readOnly { implicit session =>
      sql"""
        select jdoc from users
        where jdoc->>'_id' = $key
        or jdoc->>'primaryEmailAddress' = $key
        """
        .map(asIdentityUser)
        .single()
        .apply()
    }

    val user = result match {
      case Some(user) => \/-(user)
      case None => -\/(ApiError("User not found"))
    }

    Future.successful(user.getOrElse(throw NotFound))
  }

  def update(user: IdentityUser): Future[Int] = {
    val jdoc = Json.toJson[IdentityUser](user).toString()

    val result = DB localTx { implicit session =>
      sql"update users set jdoc = $jdoc::jsonb where jdoc->>'_id' = ${user._id}"
        .update()
        .apply()
    }

    Future.successful(result)
  }

  def update(user: User, userUpdateRequest: IdentityUserUpdate): ApiResponse[User] = {
    // get and push new doc
    val result = for {
      idUser <- findBy(user.id)
      idUpdated = prepareUserForUpdate(userUpdateRequest, idUser)
      _ <- update(idUpdated)
    } yield User.fromIdentityUser(idUpdated)

    asApiResponse(result).recover { case error =>
      val title = s"Failed to update user ${user.id}"
      logger.error(title, error)
      -\/(ApiError(title, error.getMessage))
    }
  }


  def updateEmailValidationStatus(user: User, emailValidated: Boolean): ApiResponse[User] = {
    def updateEmail(user: IdentityUser): IdentityUser = {
      val fields = user.statusFields.getOrElse(StatusFields()).copy(userEmailValidated = Some(emailValidated))
      user.copy(statusFields = Some(fields))
    }

    val result = for {
      idUser <- findBy(user.id)
      idUpdate = updateEmail(idUser)
      _ <- update(idUpdate)
    } yield User.fromIdentityUser(idUpdate)

    asApiResponse(result)
  }

  def delete(user: User): ApiResponse[Unit] = {
    val result = DB localTx { implicit session =>
      sql"delete users where jdoc->>'_id' = ${user.id}"
        .update()
        .apply()
    }

    // TODO use result when made async
    asApiResponse(Future.successful(())).recover { case error =>
      val title = s"Failed to delete user ${user.id}"
      logger.error(title, error)
      -\/(ApiError(title, error.getMessage))
    }
  }

  def unsubscribeFromMarketingEmails(email: String): ApiResponse[User] = {
    def unsubscribe(user: IdentityUser): IdentityUser = {
      val fields = user.statusFields.getOrElse(StatusFields()).copy(receive3rdPartyMarketing = Some(false), receiveGnmMarketing = Some(false))
      user.copy(statusFields = Some(fields))
    }

    val result = for {
      idUser <- findBy(email)
      idUpdate = unsubscribe(idUser)
      _ <- update(idUpdate)
    } yield User.fromIdentityUser(idUpdate)

    asApiResponse(result)
  }

  private[this] def asIdentityUser(rs: WrappedResultSet): IdentityUser = {
    val res = rs.string(1)
    println(res)
    Json.parse(res).as[IdentityUser]
  }

}
