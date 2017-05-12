package services

import javax.inject.Inject

import com.gu.identity.util.Logging
import com.google.inject.ImplementedBy
import configuration.Config.TouchpointSalesforce._
import models.{MembershipDetails, SubscriptionDetails}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.{WS, WSClient, WSResponse}
import play.api.http.Status.OK
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class SalesforceError(msg: String) extends Exception(msg)

case class SFAuthentication(access_token: String, instance_url: String)

object SFAuthentication {
  implicit val format = Json.format[SFAuthentication]
}

case class SFContact(Id: String, Name: String, Email: String, IdentityID__c: String)

object SFContact {
  implicit val format = Json.format[SFContact]
}

@ImplementedBy(classOf[Salesforce])
trait SalesforceService {
  def getSubscriptionByIdentityId(id: String): Future[Option[SubscriptionDetails]]
  def getMembershipByIdentityId(id: String): Future[Option[MembershipDetails]]

}

class Salesforce @Inject() (ws: WSClient) extends SalesforceService with Logging {

  private lazy val sfAuth: SFAuthentication = {
    val authEndpoint = s"${apiUrl}/services/oauth2/token"

    val param = Map(
      "client_id" -> consumerKey,
      "client_secret" -> consumerSecret,
      "username" -> apiUsername,
      "password" -> (apiPassword + apiToken),
      "grant_type" -> "password"
    ).map { case (k, v) => s"$k=$v" }.mkString("&")

    val request = ws.url(s"$authEndpoint?$param")

    val response = Try(Await.result(request.post(""), 10.second))

    response match {
      case Success(res) =>
        if (res.status == OK) Json.parse(res.body).as[SFAuthentication]
        else throw new SalesforceError(s"Authentication failure: ${res.body.toString}")
      case Failure(e) => throw new SalesforceError(s"${e.getMessage}")
    }
  }

  private val authHeader = ("Authorization", s"Bearer ${sfAuth.access_token}")

  def getSubscriptionByIdentityId(id: String): Future[Option[SubscriptionDetails]] = {

    val sooqlQuery =
      s"""
         |SELECT
         |    Id,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.IdentityId__c,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.Membership_Number__c,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.Email,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.Name,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.MailingCountry,
         |    Zuora__ProductName__c,
         |    Subscription_Status__c,
         |    Subscription_Name__c,
         |    Zuora__EffectiveStartDate__c,
         |    Zuora__EffectiveEndDate__c,
         |    Zuora__Subscription__r.ActivationDate__c
         |
         |FROM
         |  Zuora__SubscriptionProductCharge__c
         |
         |WHERE
         |  (Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.IdentityId__c  = '$id') AND
         |  (Subscription_Status__c = 'Active') AND
         |  (
         |    (Zuora__ProductName__c = 'Digital Pack') OR
         |    (Zuora__ProductName__c = 'Newspaper Voucher') OR
         |    (Zuora__ProductName__c = 'Newspaper Delivery') OR
         |    (Zuora__ProductName__c = 'Guardian Weekly Zone A') OR
         |    (Zuora__ProductName__c = 'Guardian Weekly Zone B') OR
         |    (Zuora__ProductName__c = 'Guardian Weekly Zone C')
         |  )
         |
         |ORDER BY
         |  Zuora__EffectiveStartDate__c DESC NULLS LAST
      """.stripMargin

    val endpoint = s"${sfAuth.instance_url}/services/data/v29.0/query"

    val request = ws.url(s"$endpoint?q=$sooqlQuery").withHeaders(authHeader)
    request.get().map { res =>
      if (res.status == OK) {
        def createSubscription(res: WSResponse): SubscriptionDetails = {
          val records: JsArray = (res.json \ "records").as[JsArray]

          SubscriptionDetails(
            tier = (records(0) \ "Zuora__ProductName__c").asOpt[String],
            subscriberId = (records(0) \ "Subscription_Name__c").asOpt[String],
            joinDate = (records(0) \ "Zuora__EffectiveStartDate__c").asOpt[String],
            end = (records(0) \ "Zuora__EffectiveEndDate__c").asOpt[String],
            activationDate = (records(0) \ "Zuora__Subscription__r" \ "ActivationDate__c").asOpt[String],
            zuoraSubscriptionName = (records(0) \ "Subscription_Name__c").asOpt[String])
        }

        if ((res.json \ "totalSize").as[Int] > 0)
          Some(createSubscription(res))
        else
          None
      }
      else {
        logger.error(s"Salesforce error. Could not get digital pack for user $id: ${res.body.toString}")
        None
      }
    }
  }

  def getMembershipByIdentityId(id: String): Future[Option[MembershipDetails]] = {
    val sooqlQuery =
      s"""
         |SELECT
         |    Id,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.IdentityId__c,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.Membership_Number__c,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.Email,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.Name,
         |    Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.MailingCountry,
         |    Zuora__ProductName__c,
         |    Subscription_Status__c,
         |    Subscription_Name__c,
         |    Zuora__EffectiveStartDate__c,
         |    Zuora__EffectiveEndDate__c
         |
         |FROM
         |  Zuora__SubscriptionProductCharge__c
         |
         |WHERE
         |  (Zuora__Subscription__r.Zuora__CustomerAccount__r.Contact__r.IdentityId__c  = '$id') AND
         |  (Subscription_Status__c = 'Active') AND
         |  ((Zuora__ProductName__c = 'Friend') OR (Zuora__ProductName__c = 'Supporter') OR
         |   (Zuora__ProductName__c = 'Partner') OR (Zuora__ProductName__c = 'Patron') OR (Zuora__ProductName__c = 'Staff Membership'))
         |
         |ORDER BY
         |  Zuora__EffectiveStartDate__c DESC NULLS LAST
      """.stripMargin

    val endpoint = s"${sfAuth.instance_url}/services/data/v29.0/query"

    val request = ws.url(s"$endpoint?q=$sooqlQuery").withHeaders(authHeader)
    request.get().map { res =>
      if (res.status == OK) {

        def createMembership(res: WSResponse): MembershipDetails = {
          val records: JsArray = (res.json \ "records").as[JsArray]

          MembershipDetails(
            tier = (records(0) \ "Zuora__ProductName__c").asOpt[String],
            membershipNumber = (records(0) \ "Zuora__Subscription__r" \ "Zuora__CustomerAccount__r" \ "Contact__r" \ "Membership_Number__c").asOpt[String],
            joinDate = Some((records(0) \ "Zuora__EffectiveStartDate__c").as[String]),
            end = Some((records(0) \ "Zuora__EffectiveEndDate__c").as[String]),
            zuoraSubscriptionName = Some((records(0) \ "Subscription_Name__c").as[String]))
        }

        if ((res.json \ "totalSize").as[Int] > 0)
          Some(createMembership(res))
        else
          None
      }
      else {
        logger.error(s"Salesforce error. Could not get membership for user $id: ${res.body.toString}")
        None
      }
    }
  }
}
