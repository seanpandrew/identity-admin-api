package configuration

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory

object Config {
  val config = ConfigFactory.load()

  val applicationName = "identity-admin-api"

  val stage = config.getString("stage")

  object AWS {
    val profile = config.getString("aws-profile")
    val credentialsProvider = new AWSCredentialsProviderChain(new ProfileCredentialsProvider(profile), new InstanceProfileCredentialsProvider())
    val region = Regions.EU_WEST_1
  }

  val hmacSecret = config.getString("hmac.secret")
  
  object IdentityApi {
    val clientToken = config.getString("identity-api.client-token")
    val baseUrl = config.getString("identity-api.base-url")
  }
}
