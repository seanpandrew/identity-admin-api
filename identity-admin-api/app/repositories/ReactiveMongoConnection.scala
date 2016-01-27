package repositories

import com.gu.identity.util.Logging
import configuration.MongoConfig
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}

import scala.concurrent.Future

object ReactiveMongoConnection extends Logging {

  private var mongoDb: Option[DefaultDB] = None

  import play.api.Play.current
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  val driver = new MongoDriver

  def registerDriverShutdownHook(mongoDriver: MongoDriver): MongoDriver = {
    current.injector.instanceOf[ApplicationLifecycle].
      addStopHook { () => Future(mongoDriver.close()) }
    mongoDriver
  }

  def init(): DefaultDB = {
    val uri = MongoConfig.uri
    registerDriverShutdownHook(driver)
    MongoConnection.parseURI(uri).map { parsedUri =>
      val connection = driver.connection(parsedUri)
      val databaseName = parsedUri.db.getOrElse(throw new Exception("No database specified in provided MongoDB URI"))
      val db = connection.db(databaseName)
      mongoDb = Some(db)
      db
    }.toOption.getOrElse(throw new Exception("Could not connect to MongoDB using provided URI"))
  }

  def db(): DefaultDB = mongoDb.getOrElse(init())

}
