package repositories

import javax.inject.{Inject, Singleton}

import com.gu.identity.util.Logging
import configuration.MongoConfig
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}

import scala.concurrent.Future

@Singleton
class ReactiveMongoConnection @Inject() (mongoConfig: MongoConfig, app: play.api.Application) extends Logging {

  private var mongoDb: Option[DefaultDB] = None

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  val driver = new MongoDriver

  def registerDriverShutdownHook(mongoDriver: MongoDriver): MongoDriver = {
    app.injector.instanceOf[ApplicationLifecycle].
      addStopHook { () => Future(mongoDriver.close()) }
    mongoDriver
  }

  def init(): DefaultDB = {
    val uri = mongoConfig.uri
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
