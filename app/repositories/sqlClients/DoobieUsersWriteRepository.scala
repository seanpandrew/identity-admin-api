package repositories.sqlClients

import com.gu.identity.util.Logging
import doobie.imports._
import models.{ApiError, ApiResponse, User}
import org.postgresql.util.PGobject
import play.api.libs.json.Json
import repositories._
import repositories.sqlClients.RepoHelpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.-\/
import scalaz.effect.IO

// See https://tpolecat.github.io/doobie-0.2.0/10-Custom-Mappings.html on JSON custom mapping

/** Thoughts:
  * - uses odd symbolds / requries some functional prog knowledge to use well
  * - given that we don't really use transactions or complicated queries, we
  *   might not get the benefits here
  * - bit worried about the heavy use of implicits and cryptic errors (even
  *   though they have made efforts to improve this, I suspect it will be hard
  *   for someone new to the library to grok these issues)
  *
  * Issues:
  * - Intellij seems confused with this file :( unless we hack it
  *   - current solution is to add type annotation to the query vals
  *   - see also their FAQ on this: http://tpolecat.github.io/doobie-scalaz-0.4.2/17-FAQ.html
  * - JDBC-based (like ScalaLike) so have to roll our own async wrapping
  */

class DoobieUsersWriteRepository extends UsersWriteRepo with Logging {

  // For Intellij - see https://github.com/tpolecat/doobie/issues/206 for why I think
//  implicit class SqlIdeFixer(sc: StringContext) extends doobie.syntax.string.SqlInterpolator(sc) {
//    def sql2[A: Composite](args: A) = this.sql.applyProduct(args)
//  }

//  implicit val DateTimeMeta: Meta[DateTime] =
//    Meta[java.sql.Timestamp].xmap(
//      ts => new DateTime(ts.getTime()),
//      dt => new java.sql.Timestamp(dt.getMillis)
//    )

/*
import repositories.sqlClients._
val client = new DoobieUsersWriteRepository
val idUser = client.findBy("21836057")

// to test out email update
import models._
val user = User.fromIdentityUser(idUser.value.get.get)
client.updateEmailValidationStatus(user, true)


*/

  implicit val identityUserMeta: Meta[IdentityUser] = {
    Meta.other[PGobject]("jsonb").xmap(
      o => {
        println("FOUND!!! " + o)
        Json.parse(o.getValue).as[IdentityUser]
      },
      idUser => {
        val json = Json.toJson[IdentityUser](idUser).toString()
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(json)
        o
      }
    )
  }

  val xa = DriverManagerTransactor[IO](
    "org.postgresql.Driver", "jdbc:postgresql:identity", "identity_admin", ""
  )

  //Composite[DateTime]

  def findBy(key: String): Future[IdentityUser] = {
    // Doesn't support triple-quotes?! so need to escape
    val query: Query0[IdentityUser] =
      sql"""
        select jdoc from users
        where jdoc->>'_id' = $key
        or jdoc->>'primaryEmailAddress' = $key
        """
      .query[IdentityUser]

    val user = query
      .option
      .transact(xa)
      .unsafePerformIO

    Future.successful(user.getOrElse(throw NotFound))
  }

  def update(user: IdentityUser): Future[Int] = {
    val jdoc = Json.toJson[IdentityUser](user).toString()

    val query: Update0 = sql"update users set jdoc = $jdoc::jsonb where jdoc->>'_id' = ${user._id}"
      .update

      val result = query
      .run
      .transact(xa)
      .unsafePerformIO()

    Future.successful(result)
  }

  def update(user: User, userUpdateRequest: IdentityUserUpdate): ApiResponse[User] = {
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
    val query: Update0 = sql"delete users where jdoc->>'_id' = ${user.id}".update

    query
      .run
      .transact(xa)
      .unsafePerformIO()

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
}
