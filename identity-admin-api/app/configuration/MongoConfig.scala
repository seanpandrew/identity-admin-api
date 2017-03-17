package configuration

import javax.inject.Inject

import com.gu.aws.{EC2, EC2DiscoveryService}
import com.gu.mongodb.AutoDiscovery

class MongoConfig @Inject() (configuration: play.api.Configuration) {

  val mongoAutoDiscovery = new AutoDiscovery(EC2DiscoveryService, EC2)

  def uri: String = {
    configuration.getString("mongodb.uri") match {
      case Some(uriFromConfig) => uriFromConfig
      case _ => {
        val uri = for {
          username <- configuration.getString("mongodb.username")
          password <- configuration.getString("mongodb.password")
          database <- configuration.getString("mongodb.database")
        } yield mongoAutoDiscovery.mongoUri(username, password, database, Config.stage, Config.stack)
        uri.getOrElse(throw new RuntimeException("Could not construct Mongo URI. Missing mongodb.username, mongodb.password or mongodb.database.")).toString
      }
    }
  }

}
