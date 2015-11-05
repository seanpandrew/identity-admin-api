package services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import models.ApiErrors
import org.scalatest.{BeforeAndAfterAll, Matchers, BeforeAndAfterEach, WordSpec}
import org.scalatestplus.play.OneServerPerSuite

import scala.concurrent.Await
import scala.concurrent.duration._
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._

class IdentityApiClientTest extends WordSpec with Matchers with BeforeAndAfterEach with OneServerPerSuite with BeforeAndAfterAll{

  val Port = 8698
  val Host = "localhost"
  val ClientToken = "TestToken"
  val UserId = "123"
  val wireMockServer = new WireMockServer(wireMockConfig().port(Port))
  
  class TestIdentityApiClient extends IdentityApiClient {
    override val baseUrl = s"http://$Host:$Port"
    override val clientToken = ClientToken
  }
  
  val client = new TestIdentityApiClient()

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(Host, Port)
  }

  override def beforeEach() {
    wireMockServer.resetMappings()
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  "sendEmailValidation" should {
    "return Right when successful" in {
      val path = s"/user/$UserId/send-validation-email"
      stubFor(post(urlEqualTo(path)).withHeader("X-GU-ID-Client-Access-Token", equalTo(s"Bearer $ClientToken"))
        .willReturn(
          aResponse()
            .withStatus(200)))
      
      val response = client.sendEmailValidation(UserId)

      val result = Await.result(response, 1.second)
      result shouldEqual Right(true)
    }
    
    "return Left when unsuccessful" in {
      val errorJson =
        """
          {
           "status":"error",
           "errors":[
            {"message":"fatal error","description":"boom"}
           ]
          }
        """
      val path = s"/user/$UserId/send-validation-email"
      stubFor(post(urlEqualTo(path)).withHeader("X-GU-ID-Client-Access-Token", equalTo(s"Bearer $ClientToken"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody(errorJson)))
      
      val response = client.sendEmailValidation(UserId)

      val result = Await.result(response, 1.second)
      result shouldEqual Left(ApiErrors.internalError("fatal error"))
    }
  }

}
