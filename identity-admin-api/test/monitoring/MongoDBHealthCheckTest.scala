package monitoring

import akka.actor.ActorSystem
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, FunSuite}
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import repositories.UsersReadRepository

import scala.concurrent.Future

class MongoDBHealthCheckTest extends FunSuite with MockitoSugar with Eventually with Matchers {

  val actorSystem = ActorSystem("test")

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  test("report as healthy when mongo query is successful") {
    val userRepo = mock[UsersReadRepository]
    val metrics = mock[Metrics]
    val hc = new MongoDBHealthCheck(userRepo, actorSystem, metrics)
    when(userRepo.count()).thenReturn(Future.successful(10))

    hc.triggerUpdate()
    eventually {
      verify(metrics).publishCount(hc.MetricName, 1)
    }
  }

  test("report as unhealthy when mongo query returns zero users") {
    val userRepo = mock[UsersReadRepository]
    val metrics = mock[Metrics]
    val hc = new MongoDBHealthCheck(userRepo, actorSystem, metrics)
    when(userRepo.count()).thenReturn(Future.successful(0))

    hc.triggerUpdate()
    eventually {
      verify(metrics).publishCount(hc.MetricName, 0)
    }
  }

  test("report as unhealthy when mongo query fails") {
    val userRepo = mock[UsersReadRepository]
    val metrics = mock[Metrics]
    val hc = new MongoDBHealthCheck(userRepo, actorSystem, metrics)
    when(userRepo.count()).thenReturn(Future.failed(new IllegalStateException("boom")))

    hc.triggerUpdate()
    eventually {
      verify(metrics).publishCount(hc.MetricName, 0)
    }
  }

}
