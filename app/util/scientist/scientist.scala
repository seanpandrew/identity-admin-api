package util.scientist

import ai.x.diff.DiffShow
import com.typesafe.scalalogging.Logger
import org.slf4j.{Logger => ILogger}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

sealed trait Result
case class Error(e: Throwable) extends Result
case class Match[A](control: A, candidate: A) extends Result
case class Ignore[A](control: A, candidate: A) extends Result
case class MisMatch[A](control: A, candidate: A) extends Result

object Defaults {

  val logger = Logger("ScientistLogger")

  def publish(logger: ILogger)(result: Result): Unit = result match {
    case Error(e) => logger.error("Scientist error", e)
    case MisMatch(control, candidate) => logger.error(DiffShow.diff(control, candidate).string)
    case _ =>
  }
}

case class SyncExperiment[A](
  name: String,
  control: () => A = () => ???,
  candidate: () => A = () => ???,
  publish: Result => Unit = println,
  isEnabled: () => Boolean = () => true,
  shouldIgnore: A => Boolean = (_: A) => false,
  isEqual: (A, A) => Boolean = (_: A) == (_: A)
) {

  def using(
    control: () => A,
    candidate: () => A): SyncExperiment[A] = {

    copy(control = control, candidate = candidate)
  }
}

case class AsyncExperiment[A](
  name: String,
  control: () => Future[A] = () => ???,
  candidate: () => Future[A] = () => ???,
  publish: Result => Unit = println,
  isEnabled: () => Boolean = () => true,
  shouldIgnore: A => Boolean = (_: A) => false,
  isEqual: (A, A) => Boolean = (_: A) == (_: A)
) {

  def using(
    control: () => Future[A],
    candidate: () => Future[A]): AsyncExperiment[A] = {

    copy(control = control, candidate = candidate)
  }
}

object Science {

  lazy val callingThreadEC: ExecutionContext = new ExecutionContext {
    override def reportFailure(cause: Throwable) = ExecutionContext.defaultReporter
    override def execute(runnable: Runnable) = runnable.run()
  }

  def run[A](sync: SyncExperiment[A]): A = {
    implicit val ec = callingThreadEC

    val async = AsyncExperiment(
      name = sync.name,
      control = () => Future.apply(sync.control()),
      candidate = () => Future.apply(sync.candidate()),
      publish = sync.publish,
      isEnabled = sync.isEnabled,
      shouldIgnore = sync.shouldIgnore,
      isEqual = sync.isEqual
    )

    Await.result(run(async), Duration.Inf)
  }

  def run[A](exp: AsyncExperiment[A])(implicit executor: ExecutionContext): Future[A] = {
    val result = exp.control()

    if (exp.isEnabled()) {
      val candidateResult = exp.candidate()

      for {
        con <- result
        can <- candidateResult
      } {
        val isEqual = exp.isEqual(con, can)

        val msg =
          if (exp.shouldIgnore(con)) Ignore(con, can)
          else if (isEqual) Match(con, can)
          else MisMatch(con, can)

        exp.publish(msg)
      }

      candidateResult.onFailure { case e => exp.publish(Error(e)) }
    }

    result
  }
}