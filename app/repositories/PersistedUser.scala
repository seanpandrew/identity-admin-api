package repositories

import MongoJsonFormats._
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.language.implicitConversions

case class SocialLink(socialId: String, 
                      network: String, 
                      profileData: Map[String, Any] = Map.empty)

object SocialLink {
  val reads: Reads[SocialLink] = (
  (JsPath \ "socialId").read[String] and
  (JsPath \ "network").read[String] and
  (JsPath \ "profileDate").readNullable[Map[String, Any]].map(_.getOrElse(Map.empty))
  )(SocialLink.apply _)

  val writes: Writes[SocialLink] = (
  (JsPath \ "socialId").write[String] and
  (JsPath \ "network").write[String] and
  (JsPath \ "profileData").write[Map[String, Any]]
  )(unlift(SocialLink.unapply))

  implicit val format: Format[SocialLink] = Format(reads, writes)
}

case class GroupMembership(path: String, 
                           packageCode: String, 
                           joinedDate: Option[DateTime] = None)

object GroupMembership {
  implicit val format = Json.format[GroupMembership]
}

case class LastActiveLocation(countryCode: Option[String] = None, 
                              cityCode : Option[String] = None)

object LastActiveLocation {
  implicit val format = Json.format[LastActiveLocation]
}


case class PrivateFields(firstName: Option[String] = None,
                          secondName: Option[String] = None,
                          gender: Option[String] = None,
                          registrationIp: Option[String] = None,
                          postcode: Option[String] = None,
                          country: Option[String] = None,
                          address1: Option[String] = None,
                          address2: Option[String] = None,
                          address3: Option[String] = None,
                          address4: Option[String] = None,
                          billingAddress1: Option[String] = None,
                          billingAddress2: Option[String] = None,
                          billingAddress3: Option[String] = None,
                          billingAddress4: Option[String] = None,
                          billingCountry: Option[String] = None,
                          billingPostcode: Option[String] = None,
                          socialAvatarUrl: Option[String] = None,
                          lastActiveIpAddress: Option[String] = None,
                          lastActiveLocation: Option[LastActiveLocation] = None,
                          registrationType: Option[String] = None,
                          registrationPlatform: Option[String] = None
                          )

object PrivateFields {
  implicit val format = Json.format[PrivateFields]
}


case class PublicFields(username: Option[String] = None,
                        usernameLowerCase: Option[String] = None,
                        displayName: Option[String] = None,
                        vanityUrl: Option[String] = None,
                        aboutMe: Option[String] = None,
                        interests: Option[String] = None,
                        webPage: Option[String] = None,
                        location: Option[String] = None,
                        avatarUrl: Option[String] = None)

object PublicFields {
  implicit val format = Json.format[PublicFields]
}

case class UserDates(lastActivityDate: Option[DateTime] = None,
                     accountCreatedDate: Option[DateTime] = Some(DateTime.now()),
                     birthDate: Option[DateTime] = None,
                     lastExportedFromDiscussion: Option[DateTime] = None)

object UserDates {
  implicit val format = Json.format[UserDates]
}

case class StatusFields(receive3rdPartyMarketing: Option[Boolean] = None,
                        receiveGnmMarketing: Option[Boolean] = None,
                        userEmailValidated: Option[Boolean] = None)

object StatusFields {
  implicit val format = Json.format[StatusFields]
}

case class SearchFields(emailAddress: Option[String] = None,
                        username: Option[String] = None,
                        displayName: Option[String] = None,
                        postcode: Option[String] = None,
                        postcodePrefix: Option[String] = None)

object SearchFields {
  implicit val format = Json.format[SearchFields]
}

sealed trait PersistedUser

case class IdentityUser(primaryEmailAddress: String,
                        _id: String,
                        publicFields: Option[PublicFields] = None,
                        privateFields: Option[PrivateFields] = None,
                        statusFields: Option[StatusFields] = None,
                        dates: Option[UserDates] = Some(new UserDates()),
                        password: Option[String] = None,
                        userGroups: List[GroupMembership] = Nil,
                        socialLinks: List[SocialLink] = Nil,
                        adData: Map[String, Any] = Map.empty,
                        searchFields: Option[SearchFields] = None
                 ) extends PersistedUser

object IdentityUser {
  val identityUserReads: Reads[IdentityUser] = (
  (JsPath \ "primaryEmailAddress").read[String] and
  (JsPath \ "_id").read[String] and
  (JsPath \ "publicFields").readNullable[PublicFields] and
  (JsPath \ "privateFields").readNullable[PrivateFields] and
  (JsPath \ "statusFields").readNullable[StatusFields] and
  (JsPath \ "dates").readNullable[UserDates] and
  (JsPath \ "password").readNullable[String] and
  (JsPath \ "userGroups").readNullable[List[GroupMembership]].map(_.getOrElse(Nil)) and
  (JsPath \ "socialLinks").readNullable[List[SocialLink]].map(_.getOrElse(Nil)) and
  (JsPath \ "adData").readNullable[Map[String, Any]].map(_.getOrElse(Map.empty)) and
  (JsPath \ "searchFields").readNullable[SearchFields])(IdentityUser.apply _)

  val identityUserWrites: OWrites[IdentityUser] = (
  (JsPath \ "primaryEmailAddress").write[String] and
  (JsPath \ "_id").write[String] and
  (JsPath \ "publicFields").writeNullable[PublicFields] and
  (JsPath \ "privateFields").writeNullable[PrivateFields] and
  (JsPath \ "statusFields").writeNullable[StatusFields] and
  (JsPath \ "dates").writeNullable[UserDates] and
  (JsPath \ "password").writeNullable[String] and
  (JsPath \ "userGroups").write[List[GroupMembership]] and
  (JsPath \ "socialLinks").write[List[SocialLink]] and
  (JsPath \ "adData").write[Map[String, Any]] and
  (JsPath \ "searchFields").writeNullable[SearchFields]
)(unlift(IdentityUser.unapply))

  implicit val format: OFormat[IdentityUser] = OFormat(identityUserReads, identityUserWrites)
}

case class Orphan(id: String = "orphan", email: String) extends PersistedUser
