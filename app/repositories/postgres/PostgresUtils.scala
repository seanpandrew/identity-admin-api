package repositories.postgres

import com.gu.identity.util.Logging
import models.{ApiError, ApiResponse}
import scalikejdbc.{DB, DBSession}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/

trait PostgresUtils {
  self: Logging =>

  def logFailure(msg: String): Throwable => ApiError = { t =>
    logger.error(msg, t)
    ApiError(msg, t.getMessage)
  }

  def readOnly[T](f: DBSession => T)
                 (recover: Throwable => ApiError)
                 (implicit ec: ExecutionContext): ApiResponse[T] = Future {
    \/.fromTryCatchNonFatal(DB.readOnly(f)).leftMap(recover)
  }

  def localTx[T](f: DBSession => T)
                (recover: Throwable => ApiError)
                (implicit ec: ExecutionContext): ApiResponse[T] = Future {
    \/.fromTryCatchNonFatal(DB.localTx(f)).leftMap(recover)
  }

}
