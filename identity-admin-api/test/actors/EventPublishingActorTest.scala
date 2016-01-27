package actors

import actors.EventPublishingActor.{EmailValidationChanged, DisplayNameChanged}
import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpec, Matchers}
import org.mockito.Mockito._

class EventPublishingActorTest extends WordSpec with MockitoSugar with Matchers {
  implicit val system = ActorSystem("TestActorSystem")

  val mockSnsClient = mock[AmazonSNSAsyncClient]

  "EventPublishingActor" should {
    val actorRef = TestActorRef(new EventPublishingActor(mockSnsClient))

    "send an event to SNS when a DisplayNameChanged event is received" in {
      val message = DisplayNameChanged("1234")
      val expectedPublishRequest = new PublishRequest(
        "arn2",
        """{"userId":"1234"}""",
        "Display Name Changed")

      actorRef ! message

      verify(mockSnsClient, times(1)).publishAsync(expectedPublishRequest)
    }

    "send an event to SNS when a EmailValidationChanged event is received" in {
      val message = EmailValidationChanged("1234")
      val expectedPublishRequest = new PublishRequest(
        "arn1",
        """{"userId":"1234"}""",
        "E-mail Validation Changed")

      actorRef ! message

      verify(mockSnsClient, times(1)).publishAsync(expectedPublishRequest)
    }

    "does not try to send an event if an unknown message is received" in {
      case class UnknownMessage(UserId: String)

      val message = UnknownMessage("1234")

      actorRef ! message

      verify(mockSnsClient, never()).publishAsync(new PublishRequest())
    }

  }

}
