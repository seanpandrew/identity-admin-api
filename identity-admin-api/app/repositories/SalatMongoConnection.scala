package repositories


import com.gu.identity.util.Logging
import com.mongodb.WriteConcern
import com.mongodb.casbah._
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import play.api.Play
import play.api.Play.current

object SalatMongoConnection extends Logging {
  private var mongoDb: Option[MongoDB] = None

  RegisterJodaTimeConversionHelpers()
  
  private def init(): MongoDB = {
    val uri = Play.configuration.getString("mongodb.uri").getOrElse(throw new IllegalStateException("mongodb.uri is not set"))
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
