package monitoring

import akka.actor.ActorSystem
import com.gu.identity.util.Logging
import repositories.UsersReadRepository

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class MongoDBHealthCheck(userRepository: UsersReadRepository, actorSystem: ActorSystem, metrics: Metrics) extends Logging {

  private[monitoring] val MetricName = "MongoConnectivity"

  private[monitoring] def triggerUpdate() {
    logger.debug("Updating mongo connectivity health check")

    userRepository.count().onComplete {
      case Success(count) =>
        if(count > 0)
          metrics.publishCount(MetricName, 1)
        else {
          logger.error("Mongo DB is reporting zero users")
          metrics.publishCount(MetricName, 0)
        }
      case Failure(t) =>
        logger.error("Error connecting to mongo", t)
        metrics.publishCount(MetricName, 0)
    }
  }

  def start() {
    // trigger immediately
    triggerUpdate()

    // trigger every minute
    actorSystem.scheduler.schedule(1.minute, 1.minute) {
      triggerUpdate()
    }
  }
}
