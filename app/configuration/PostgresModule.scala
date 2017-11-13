package configuration

import javax.sql.DataSource

import com.google.inject.AbstractModule
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

import Config.Postgres

class PostgresModule extends AbstractModule {
  override def configure(): Unit = {
    val dataSource: DataSource = {
      val config = new HikariConfig()
      config.setConnectionTestQuery("SELECT 1")
      config.setJdbcUrl(Postgres.jdbcUrl)
      config.setUsername(Postgres.username)
      config.setPassword(Postgres.password)
      val ds = new HikariDataSource(
        config
      )
      ds
    }
    ConnectionPool.add("postgres", new DataSourceConnectionPool(dataSource))
    val pool = ConnectionPool.get("postgres")
    bind(classOf[ConnectionPool]).toInstance(pool)
  }
}
