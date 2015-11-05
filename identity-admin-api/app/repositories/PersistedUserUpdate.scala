package repositories

import models.UserUpdateRequest

case class PersistedUserUpdate(
                              email: String,
                              username: String,
                              firstName: Option[String] = None,
                              lastName: Option[String] = None,
                              receiveGnmMarketing: Option[Boolean] = None,
                              receive3rdPartyMarketing: Option[Boolean] = None,
                              userEmailValidated: Option[Boolean] = None)

object PersistedUserUpdate {
  def apply(userUpdateRequest: UserUpdateRequest, userEmailValidated: Option[Boolean]): PersistedUserUpdate =
    PersistedUserUpdate(
      email = userUpdateRequest.email,
      username = userUpdateRequest.username,
      firstName = userUpdateRequest.firstName,
      lastName = userUpdateRequest.lastName,
      receiveGnmMarketing = userUpdateRequest.receiveGnmMarketing,
      receive3rdPartyMarketing = userUpdateRequest.receive3rdPartyMarketing,
      userEmailValidated = userEmailValidated
    )
}
