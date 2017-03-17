package repositories

import javax.inject.Inject

import com.gu.identity.util.Logging
import com.mongodb.WriteConcern
import com.mongodb.casbah._
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import configuration.MongoConfig

class SalatMongoConnection @Inject() (mongoConfig: MongoConfig) extends Logging {
  private var mongoDb: Option[MongoDB] = None

  RegisterJodaTimeConversionHelpers()
  
  private def init(): MongoDB = {
    val uri = mongoConfig.uri
    val mongoUri = MongoClientURI(uri)
    val connection: MongoClient = MongoClient(mongoUri)
    mongoUri.database.map { dbName =>
      val db = connection(dbName)
      db.writeConcern = WriteConcern.ACKNOWLEDGED
      mongoDb = Some(db)
      db
    } getOrElse(throw new RuntimeException("Could not connect to mongo"))
  }
  
  def db(): MongoDB = mongoDb.getOrElse(init())

}
