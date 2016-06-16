package services

import com.google.inject.ImplementedBy
import configuration.Config.TouchpointSalesforce._
import models.{MembershipDetails, SubscriptionDetails}
import play.api.Play._
import play.api.libs.json.{JsArray, JsResult, Json}
import play.api.libs.ws.{WS, WSRequest, WSResponse}
import play.api.http.Status.OK
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

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

class Salesforce extends SalesforceService {

  private lazy val sfAuth = {
    val authEndpoint = s"${apiUrl}/services/oauth2/token"

    val param = Map(
      "client_id" -> consumerKey,
      "client_secret" -> consumerSecret,
      "username" -> apiUsername,
      "password" -> (apiPassword + apiToken),
      "grant_type" -> "password"
    ).map { case (k, v) => s"$k=$v" }.mkString("&")

    val request = WS.url(s"$authEndpoint?$param")

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
         |   Id,
         |   Name,
         |   tp_Products_Summary__c,
         |   Zuora__Status__c,
         |   Zuora__SubscriptionStartDate__c,
         |   Zuora__SubscriptionEndDate__c,
         |   Zuora__MRR__c,
         |   (SELECT
         |       Billing_Account_Currency__c
         |    FROM
         |       Zuora__Subscription__c.Zuora__Subscription_Product_Charges__r),
         |   Zuora__CustomerAccount__c,
         |   Zuora__CustomerAccount__r.Contact__r.Id ,
         |   Zuora__CustomerAccount__r.Contact__r.Name,
         |   Zuora__CustomerAccount__r.Contact__r.IdentityID__c,
         |   Zuora__CustomerAccount__r.Contact__r.Email,
         |   Zuora__CustomerAccount__r.Contact__r.Membership_Number__c,
         |   Zuora__CustomerAccount__r.Contact__r.MailingCountry
         |
        |FROM
         |   Zuora__Subscription__c
         |
        |WHERE
         |   (Zuora__CustomerAccount__r.Contact__r.IdentityID__c='$id') AND
         |   (Zuora__Status__c = 'Active') AND
         |   (tp_Products_Summary__c = 'Digital Pack')
      """.stripMargin

    val endpoint = s"${sfAuth.instance_url}/services/data/v29.0/query"

    val request = WS.url(s"$endpoint?q=$sooqlQuery").withHeaders(authHeader)
    request.get().map { res =>
      if (res.status == OK) {
        def createSubscription(res: WSResponse): SubscriptionDetails = {
          val records: JsArray = (res.json \ "records").as[JsArray]

          SubscriptionDetails(
            tier = Some((records(0) \ "tp_Products_Summary__c").as[String]),
            subscriberId = Some((records(0) \ "Name").as[String]),
            joinDate = Some((records(0) \ "Zuora__SubscriptionStartDate__c").as[String]),
            end = Some((records(0) \ "Zuora__SubscriptionEndDate__c").as[String]),
            mrr = Some((records(0) \ "Zuora__MRR__c").as[Double].toString),
            currency = Some(((records(0) \ "Zuora__Subscription_Product_Charges__r" \ "records")(0) \ "Billing_Account_Currency__c").as[String]),
            zuoraSubscriptionName = Some((records(0) \ "Name").as[String]))
        }

        if ((res.json \ "totalSize").as[Int] > 0)
          Some(createSubscription(res))
        else
          None
      }
      else {
        throw new SalesforceError(s"Could not get subscriptions with identity ID $id: ${res.body.toString}")
      }
    }
  }

  def getMembershipByIdentityId(id: String): Future[Option[MembershipDetails]] = {
    val sooqlQuery =
      s"""
         |SELECT
         |   Id,
         |   Name,
         |   tp_Product_Types_Summary__c,
         |   tp_Products_Summary__c,
         |   Zuora__Status__c,
         |   Zuora__SubscriptionStartDate__c,
         |   Zuora__SubscriptionEndDate__c,
         |   Zuora__MRR__c,
         |   (SELECT
         |       Billing_Account_Currency__c
         |    FROM
         |       Zuora__Subscription__c.Zuora__Subscription_Product_Charges__r),
         |   Zuora__CustomerAccount__c,
         |   Zuora__CustomerAccount__r.Contact__r.Id ,
         |   Zuora__CustomerAccount__r.Contact__r.Name,
         |   Zuora__CustomerAccount__r.Contact__r.IdentityID__c,
         |   Zuora__CustomerAccount__r.Contact__r.Email,
         |   Zuora__CustomerAccount__r.Contact__r.Membership_Number__c,
         |   Zuora__CustomerAccount__r.Contact__r.MailingCountry
         |
        |FROM
         |   Zuora__Subscription__c
         |
        |WHERE
         |   (Zuora__CustomerAccount__r.Contact__r.IdentityID__c='$id') AND
         |   (Zuora__Status__c = 'Active') AND
         |   (tp_Product_Types_Summary__c = 'Membership')
      """.stripMargin

    val endpoint = s"${sfAuth.instance_url}/services/data/v29.0/query"

    val request = WS.url(s"$endpoint?q=$sooqlQuery").withHeaders(authHeader)
    request.get().map { res =>
      if (res.status == OK) {

        def createMembership(res: WSResponse): MembershipDetails = {
          val records: JsArray = (res.json \ "records").as[JsArray]

          MembershipDetails(
            tier = Some((records(0) \ "tp_Products_Summary__c").as[String]),
            membershipNumber = Some((records(0) \ "Zuora__CustomerAccount__r" \ "Contact__r" \ "Membership_Number__c").as[String]),
            joinDate = Some((records(0) \ "Zuora__SubscriptionStartDate__c").as[String]),
            end = Some((records(0) \ "Zuora__SubscriptionEndDate__c").as[String]),
            mrr = Some((records(0) \ "Zuora__MRR__c").as[Double].toString),
            currency = Some(((records(0) \ "Zuora__Subscription_Product_Charges__r" \ "records")(0) \ "Billing_Account_Currency__c").as[String]),
            zuoraSubscriptionName = Some((records(0) \ "Name").as[String]))
        }

        if ((res.json \ "totalSize").as[Int] > 0)
          Some(createMembership(res))
        else
          None
      }
      else {
        throw new SalesforceError(s"Could not get subscriptions with identity ID $id: ${res.body.toString}")
      }
    }
  }
}
//}
