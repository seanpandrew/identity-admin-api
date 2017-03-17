package filters

import javax.inject.Inject
import akka.stream.Materializer
import play.api.Logger
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class LogRequestsFilter @Inject() (implicit val mat: Materializer) extends Filter {

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
