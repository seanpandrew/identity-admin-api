package actors

import actors.EventPublishingActor.UserChangedMessage
import akka.actor.ActorRef
import com.google.inject.{Provides, Singleton}
import play.api.libs.concurrent.Akka
import services.SimpleNotificationService

@Provides @Singleton
class EventPublishingActorProvider() {

  import play.api.Play.current

  val actor: ActorRef = Akka.system.actorOf(EventPublishingActor.getProps(SimpleNotificationService.client))

  def sendEvent(event: UserChangedMessage): Unit = {
    actor ! event
  }

}