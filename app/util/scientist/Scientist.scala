package util.scientist

import ai.x.diff._
import cats.{Id, Monad, MonadError}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

sealed trait Result extends Product with Serializable
case class ExperimentFailure(e: String) extends Result
case class Match[A](control: A, candidate: A) extends Result
case class DisabledExperiment(name: String) extends Result
case class MisMatch[A](control: A, candidate: A, diffShow: DiffShow[A]) extends Result {
  override def equals(obj: scala.Any): Boolean = obj match {
    case MisMatch(control, candidate, _) =>
      this.control.equals(control) && this.candidate.equals(candidate)
    case _ => false
  }
}

object Defaults {
  lazy val log = LoggerFactory.getLogger("scientist")
  def loggingReporter[A: Manifest]: Experiment.Reporter[A] = (a: A) => {
    case ExperimentFailure(e) => log.error(s"Scientist error encountered processing for contol: $a", e)
    case MisMatch(control: A, candidate: A, ds: DiffShow[A]) =>
      log.error(ds.diff(control, candidate).string)
    case Match(_, _) => log.info(s"Successful comparison for ${implicitly[Manifest[A]].runtimeClass.getSimpleName}")
    case _ =>
  }
}

case class ExperimentSettings[A](reporter: Experiment.Reporter[A]) {
  def isEnabled(experimentName: String): Boolean = true
}

object ExperimentSettings {
  implicit def experimentSettings[T: Manifest](implicit d: DiffShow[T]): ExperimentSettings[T] =
    ExperimentSettings(Defaults.loggingReporter[T])
}

object Experiment {
  type Reporter[A] = A => Result => Unit

  implicit def errorMonadForId(implicit idMonad: Monad[Id]): MonadError[Id, Throwable] = new MonadError[Id, Throwable] {
    override def flatMap[A, B](fa: Id[A])(f: A => Id[B]) = idMonad.flatMap(fa)(f)
    override def tailRecM[A, B](a: A)(f: A => Id[Either[A, B]]) = idMonad.tailRecM(a)(f)
    override def raiseError[A](e: Throwable): Id[A] = throw e
    override def handleErrorWith[A](fa: Id[A])(f: Throwable => Id[A]) = try {
      fa
    } catch {
      case NonFatal(e) => f(e)
    }
    override def pure[A](x: A) = idMonad.pure(x)
  }

  def async[A: Manifest](name: String, control: => Future[A], candidate: => Future[A])
              (implicit ec: ExecutionContext, settings: ExperimentSettings[A],
               m: MonadError[Future, Throwable], diffShow: DiffShow[A]): Experiment[A, Future, Throwable] =
    apply[A, Future, Throwable](name, control, candidate)

  def sync[A: Manifest](name: String, control: => A, candidate: => A)
             (implicit settings: ExperimentSettings[A], diffShow: DiffShow[A]): Experiment[A, Id, Throwable] =
    apply[A, Id, Throwable](name, control, candidate)

  def apply[A: Manifest, M[_], E](name: String, control: => M[A], candidate: => M[A])
                       (implicit settings: ExperimentSettings[A],
                        m: MonadError[M, E],
                        diffShow: DiffShow[A]): Experiment[A, M, E] =
    new Experiment[A, M, E] {
      override lazy val _name: String = name
      override lazy val _control = () => control
      override lazy val _candidate = () => candidate
      override lazy val _diffShow = diffShow
    }
}

sealed trait Experiment[A, M[_], E] {
  def _name: String
  protected def _control: () => M[A]
  protected def _candidate: () => M[A]
  protected def _diffShow: DiffShow[A]

  final def run(implicit m: MonadError[M, E], settings: ExperimentSettings[A]): M[A] = {
    val controlValue = _control()
    if (settings.isEnabled(_name)) {
      val experimentResult: M[Result] = try {
        val candidateValue = _candidate()
        m.flatMap(controlValue)(cont => {
          m.handleErrorWith(
            m.map(candidateValue) { cand =>
              if (cont.equals(cand))
                Match(cont, cand)
              else
                MisMatch(cont, cand, _diffShow)
            }
          )(e => m.pure(ExperimentFailure(e.toString)))
        })
      } catch {
        case NonFatal(e) => m.pure(ExperimentFailure(s"${e.getMessage} \n ${e.getStackTrace.mkString("\n")}"))
      }
      m.map2(controlValue, experimentResult){case (control, result) => settings.reporter(control)(result)}
    } else {
      m.map2(controlValue, m.pure(DisabledExperiment(_name))){case (control, result) => settings.reporter(control)(result)}
    }
    controlValue
  }
}
