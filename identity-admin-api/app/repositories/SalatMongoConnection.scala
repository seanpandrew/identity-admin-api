package repositories


import com.gu.identity.util.Logging
import com.mongodb.WriteConcern
import com.mongodb.casbah._
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import play.api.Play
import play.api.Play.current
import com.novus.salat.global._


object SalatMongoConnection extends Logging {
  private var mongoDb: Option[MongoDB] = None

  RegisterJodaTimeConversionHelpers()
  
  private def init() = {
    val uri = Play.configuration.getString("mongodb.uri").getOrElse(throw new IllegalStateException("mongodb.uri is not set"))
    val mongoUri = MongoClientURI(uri)
    val connection: MongoClient = MongoClient(mongoUri)
    val (dbName, userName, password) = (mongoUri.database.get, mongoUri.username.get, mongoUri.password.get.mkString)

    val db = connection(dbName)
    logger.debug("authenticating with username '%s' and databasename '%s'" format (userName, dbName))

    //for future reference this command will not work if slaveOk is true
    if(db.authenticate(userName, password)) {
      db.writeConcern = WriteConcern.ACKNOWLEDGED
      mongoDb = Some(db)
      db
    } else {
      throw new RuntimeException("Failed to authenticate")
    }
  }
  
  def db(): MongoDB = mongoDb.getOrElse(init())

}
