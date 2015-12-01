import com.novus.salat.global.{ctx => SalatGlobalContext}
import configuration.Config
import filters.{AddEC2InstanceHeader, LogRequestsFilter}
import models.ApiErrors._
import monitoring.{Metrics, MongoDBHealthCheck}
import play.api.libs.concurrent.Akka.system
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{RequestHeader, Result, WithFilters}
import play.api.{Application, Logger, Play}
import repositories.UsersReadRepository
import play.api.Play.current

import scala.concurrent.Future

object Global extends WithFilters(AddEC2InstanceHeader, LogRequestsFilter) {

  private val logger = Logger(this.getClass)

  override def onBadRequest(request: RequestHeader, error: String): Future[Result] = {
    logger.debug(s"Bad request: $request, error: $error")
    Future { badRequest(error) }
  }

  override def onHandlerNotFound(request: RequestHeader): Future[Result] = {
    logger.debug(s"Handler not found for request: $request")
    Future { notFound }
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    logger.error(s"Error handling request request: $request", ex)
    Future { internalError(ex.getMessage) }
  }

  override def onStart(app: Application) = {
    SalatGlobalContext.clearAllGraters()
    SalatGlobalContext.registerClassLoader(Play.classloader(Play.current))

    if(Config.Monitoring.mongoEnabled) {
      val userRepo = app.injector.instanceOf(classOf[UsersReadRepository])
      val metrics = app.injector.instanceOf(classOf[Metrics])
      new MongoDBHealthCheck(userRepo, system, metrics).start()
    }
  }
}
