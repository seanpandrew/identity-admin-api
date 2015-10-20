package models

object ApiErrors {
  def badRequest(msg: String): ApiError =
    ApiError(
      message = "Bad Request",
      details = msg,
      statusCode = 400
    )

  val notFound: ApiError =
    ApiError(
      message = "Not found",
      details = "Not Found",
      statusCode = 404
    )

  def internalError(msg: String): ApiError =
    ApiError(
      message = "Internal Server Error",
      details = msg,
      statusCode = 500
    )

  val unauthorized = ApiError(
    message = "Unauthorized",
    details = "Failed to authenticate",
    statusCode = 401
  )
}

