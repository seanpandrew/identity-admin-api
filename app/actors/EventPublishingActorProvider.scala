package actors

import javax.inject.Inject

import actors.EventPublishingActor.UserChangedMessage
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Provides
import services.SimpleNotificationService

@Provides
class EventPublishingActorProvider @Inject() (system: ActorSystem) {

  val actor: ActorRef = system.actorOf(EventPublishingActor.getProps(SimpleNotificationService.client))

  def sendEvent(event: UserChangedMessage): Unit = {
    actor ! event
  }

}