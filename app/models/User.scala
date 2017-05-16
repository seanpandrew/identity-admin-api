package models

import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import repositories.IdentityUser

import scala.language.implicitConversions
import MongoJsFormats._

case class PersonalDetails(firstName: Option[String] = None,
                           lastName: Option[String] = None,
                           gender: Option[String] = None,
                           dateOfBirth: Option[LocalDate] = None,
                           aboutMe: Option[String] = None,
                           interests: Option[String] = None,
                           location: Option[String] = None)

object PersonalDetails {
  implicit val format = Json.format[PersonalDetails]
}

case class Address(addressLine1: Option[String] = None,
                   addressLine2: Option[String] = None,
                   addressLine3: Option[String] = None,
                   addressLine4: Option[String] = None,
                   country: Option[String] = None,
                   postcode: Option[String] = None)

object Address {
  implicit val format = Json.format[Address]
}

case class UserStatus(receive3rdPartyMarketing: Option[Boolean] = None,
                      receiveGnmMarketing: Option[Boolean] = None,
                      userEmailValidated: Option[Boolean] = None)

object UserStatus {
  implicit val format = Json.format[UserStatus]
}

case class UserGroup(packageCode: String,
                     path:String,
                     joinDate: Option[DateTime])

object UserGroup {
  implicit val format = Json.format[UserGroup]
}

case class SocialLink(socialId: String,
                      network: String)

object SocialLink {
  implicit val format = Json.format[SocialLink]
}

case class LastActiveLocation(countryCode: Option[String] = None,
                              cityCode : Option[String] = None)

object LastActiveLocation {
  implicit val format = Json.format[LastActiveLocation]
}

case class MembershipDetails(
  tier: Option[String] = None,
  membershipNumber: String,
  joinDate: Option[String] = None,
  end: Option[String] = None,
  zuoraSubscriptionName: Option[String] = None,
  identityId: String,
  email: String
)

object MembershipDetails {
  implicit val format = Json.format[MembershipDetails]
}

case class SubscriptionDetails(
  tier: Option[String] = None,
  subscriberId: Option[String] = None,
  joinDate: Option[String] = None,
  end: Option[String] = None,
  activationDate: Option[String] = None,
  zuoraSubscriptionName: Option[String] = None,
  identityId: Option[String] = None,
  email: Option[String] = None
)

object SubscriptionDetails {
  implicit val format = Json.format[SubscriptionDetails]
}

case class User(id: String,
                email: String,
                displayName: Option[String] = None,
                username: Option[String] = None,
                vanityUrl: Option[String] = None,
                personalDetails: PersonalDetails = PersonalDetails(),
                deliveryAddress: Address = Address(),
                billingAddress: Address = Address(),
                lastActivityDate: Option[DateTime] = None,
                lastActivityIp: Option[String] = None,
                registrationType: Option[String] = None,
                registrationDate: Option[DateTime] = None,
                registrationIp: Option[String] = None,
                status: UserStatus = UserStatus(),
                groups: Seq[UserGroup] = Nil,
                socialLinks: Seq[SocialLink] = Nil,
                membershipDetails: Option[MembershipDetails] = None,
                subscriptionDetails: Option[SubscriptionDetails] = None,
                hasCommented: Boolean = false,
                deleted: Boolean = false,
                orphan: Boolean = false
               )

object User {
  implicit val format = Json.format[User]

  implicit def userResponseToResult(userResponse: User): Result =
    Results.Ok(Json.toJson(userResponse))

  def fromIdentityUser(user: IdentityUser): User =
    User(
                id = user._id.getOrElse(throw new IllegalStateException("User must have an id")),
                email = user.primaryEmailAddress,
                displayName = user.publicFields.flatMap(_.displayName),
                username = user.publicFields.flatMap(_.username),
                vanityUrl = user.publicFields.flatMap(_.vanityUrl),
                personalDetails = PersonalDetails(
                 firstName = user.privateFields.flatMap(_.firstName),
                 lastName = user.privateFields.flatMap(_.secondName),
                 gender = user.privateFields.flatMap(_.gender),
                 dateOfBirth = user.dates.flatMap(_.birthDate.map(_.toLocalDate)),
                 location = user.publicFields.flatMap(_.location),
                 aboutMe = user.publicFields.flatMap(_.aboutMe),
                 interests = user.publicFields.flatMap(_.interests)
                ),
                deliveryAddress = Address(
                  addressLine1 = user.privateFields.flatMap(_.address1),
                  addressLine2 = user.privateFields.flatMap(_.address2),
                  addressLine3 = user.privateFields.flatMap(_.address3),
                  addressLine4 = user.privateFields.flatMap(_.address4),
                  country = user.privateFields.flatMap(_.country),
                  postcode = user.privateFields.flatMap(_.postcode)
                ),
                billingAddress = Address(
                  addressLine1 = user.privateFields.flatMap(_.billingAddress1),
                  addressLine2 = user.privateFields.flatMap(_.billingAddress2),
                  addressLine3 = user.privateFields.flatMap(_.billingAddress3),
                  addressLine4 = user.privateFields.flatMap(_.billingAddress4),
                  country = user.privateFields.flatMap(_.billingCountry),
                  postcode = user.privateFields.flatMap(_.billingPostcode)
                ),
                lastActivityDate = user.dates.flatMap(_.lastActivityDate),
                lastActivityIp = user.privateFields.flatMap(_.lastActiveIpAddress),
                registrationDate = user.dates.flatMap(_.accountCreatedDate),
                registrationIp = user.privateFields.flatMap(_.registrationIp),
                registrationType = user.privateFields.flatMap(_.registrationType),
                status = UserStatus(
                  receive3rdPartyMarketing = user.statusFields.flatMap(_.receive3rdPartyMarketing),
                  receiveGnmMarketing = user.statusFields.flatMap(_.receiveGnmMarketing),
                  userEmailValidated = user.statusFields.flatMap(_.userEmailValidated)
                ),
                groups = user.userGroups.map(g => UserGroup(g.packageCode, g.path, g.joinedDate)),
                socialLinks = user.socialLinks.map(s => SocialLink(s.socialId, s.network))
    )
}
