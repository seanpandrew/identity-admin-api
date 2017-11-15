package support

import org.scalatest.TestSuite
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import scalikejdbc.ConnectionPool

trait OneServerPerSuite extends GuiceOneServerPerSuite with MockitoSugar {
  self: TestSuite =>

  protected val mockConnectionPool = mock[ConnectionPool]

  override def fakeApplication() =
    new GuiceApplicationBuilder()
    .bindings(new MockPostgresModule(mockConnectionPool))
    .build()
}
