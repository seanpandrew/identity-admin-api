package configuration

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

class PostgresModule(configuration: Config) extends AbstractModule {
  override def configure() = {
    val jdbcUrl = configuration.getString("postgres.jdbcUrl")
    ConnectionPool.add("postgres", new DataSourceConnectionPool(

    ))
    val pool = ConnectionPool.get("postgres")
    bind(classOf[ConnectionPool]).toInstance(pool)
  }
}
