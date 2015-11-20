package filters

import play.api.Logger
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import Headers._

object LogRequestsFilter extends Filter {

  private val logger = Logger(this.getClass)

  def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    val start = System.currentTimeMillis

    def logTime(result: Result) {
      if(!rh.path.contains("/healthcheck")) {
        val time = System.currentTimeMillis - start
        val activity = s"${rh.method} ${rh.path}"
        logger.info(s"$activity completed in $time ms with status ${result.header.status}")
      }
    }

    val result = f(rh)

    result.foreach(logTime)

    result
  }
}
