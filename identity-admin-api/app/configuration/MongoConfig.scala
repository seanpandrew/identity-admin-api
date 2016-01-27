package configuration

import com.gu.aws.{EC2, EC2DiscoveryService}
import com.gu.mongodb.AutoDiscovery
import play.api.Play
import play.api.Play.current

object MongoConfig {

  val mongoAutoDiscovery = new AutoDiscovery(EC2DiscoveryService, EC2)

  def uri: String = {
    Play.configuration.getString("mongodb.uri") match {
      case Some(uriFromConfig) => uriFromConfig
      case _ => {
        val uri = for {
          username <- Play.configuration.getString("mongodb.username")
          password <- Play.configuration.getString("mongodb.password")
          database <- Play.configuration.getString("mongodb.database")
        } yield mongoAutoDiscovery.mongoUri(username, password, database, Config.stage, Config.stack)
        uri.getOrElse(throw new RuntimeException("Could not construct Mongo URI. Missing mongodb.username, mongodb.password or mongodb.database.")).toString
      }
    }
  }

}
