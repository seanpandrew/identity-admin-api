import scala.concurrent.Future
import scalaz.\/

package object models {
  type ApiResponse[T] = Future[ApiError \/ T]
}
