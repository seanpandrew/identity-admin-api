package monitoring

import com.amazonaws.regions.{Region, ServiceAbbreviations}
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest}
import com.google.inject.ImplementedBy
import com.gu.identity.util.Logging
import configuration.Config
import scala.util.{Success, Failure, Try}

@ImplementedBy(classOf[CloudWatch])
trait Metrics {
  def publishCount(name : String, count: Double): Unit
}

class CloudWatch extends Metrics with Logging {

  private val application = Config.applicationName
  private val stageDimension = new Dimension().withName("Stage").withValue(Config.stage)
  private val mandatoryDimensions:Seq[Dimension] = Seq(stageDimension)

  private val cloudWatch = {
    val client = new AmazonCloudWatchAsyncClient(Config.AWS.credentialsProvider)
    client.setEndpoint(Region.getRegion(Config.AWS.region).getServiceEndpoint(ServiceAbbreviations.CloudWatch))
    client
  }

  def publishCount(name : String, count: Double): Unit = {
    val metric = new MetricDatum()
      .withValue(count)
      .withMetricName(name)
      .withUnit("Count")
      .withDimensions(mandatoryDimensions: _*)

    val request = new PutMetricDataRequest().
    withNamespace(application).withMetricData(metric)

    Try(cloudWatch.putMetricDataAsync(request)) match {
      case Success(_) => logger.debug(s"Published metric to CloudWatch: name=$name value=$count")
      case Failure(e) => logger.error(s"Could not publish metric to Cloudwatch: name=$name value=$count error=${e.getMessage}}")
    }
  }
}
