package configuration

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}
import javax.sql.DataSource
import com.zaxxer.hikari.HikariDataSource

class PostgresModule(configuration: Config) extends AbstractModule {
  override def configure() = {
    val jdbcUrl = configuration.getString("postgres.jdbcUrl")
    val dataSource: DataSource = {
      val ds = new HikariDataSource()
      //ds.setDataSourceClassName(dataSourceClassName)
      ds.addDataSourceProperty("url", jdbcUrl)
      ds
    }
    ConnectionPool.add("postgres", new DataSourceConnectionPool(dataSource))
    val pool = ConnectionPool.get("postgres")
    bind(classOf[ConnectionPool]).toInstance(pool)
  }
}
