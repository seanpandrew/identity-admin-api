package models

case class ValidationError(message: String)

object UserUpdateRequestValidator {

  def isValid(userUpdateRequest: UserUpdateRequest): Either[ValidationError, UserUpdateRequest] = {
    val validUsernameAndDisplayName = (userUpdateRequest.username, userUpdateRequest.displayName) match {
      case (Some(newUsername), Some(newDisplayName)) => newUsername == newDisplayName
      case _ => true
    }
    if (validUsernameAndDisplayName) {
      Right(userUpdateRequest)
    } else {
      Left(ValidationError("Conflict between username and display name"))
    }
  }

}
