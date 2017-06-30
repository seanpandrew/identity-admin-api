package services

import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import configuration.Config

object SimpleNotificationService {

  val client = AmazonSNSAsyncClientBuilder.standard()
      .withCredentials(Config.AWS.credentialsProvider)
      .withRegion(Config.AWS.region)
      .build()
}
