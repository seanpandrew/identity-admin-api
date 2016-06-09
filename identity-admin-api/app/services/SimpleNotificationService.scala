package services

import com.amazonaws.regions.{Region, ServiceAbbreviations}
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import configuration.Config

object SimpleNotificationService {

  val client = {
    val client = new AmazonSNSAsyncClient(Config.AWS.credentialsProvider)
    client.setEndpoint(Region.getRegion(Config.AWS.region).getServiceEndpoint(ServiceAbbreviations.SNS))
    client
  }

}
