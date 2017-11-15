package support

import play.api.{Configuration, Environment}
import play.api.inject.Module
import scalikejdbc.ConnectionPool


class MockPostgresModule(pool: ConnectionPool) extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind(classOf[ConnectionPool]).toInstance(pool)
    )
  }
}
