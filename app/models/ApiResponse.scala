import scala.concurrent.Future

package object models {
  type ApiResponse[T] = Future[Either[ApiError,T]]
}
