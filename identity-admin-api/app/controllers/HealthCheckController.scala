package controllers

import javax.inject.Inject

import actions.AuthenticatedAction
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Results}

case class Test(name: String, result: () => Boolean)

class HealthCheckController @Inject() (auth: AuthenticatedAction) extends Results {

  // TODO add a meaningful test
  val tests: Seq[Test] = Nil

  def authHealthCheck() = auth {
    Ok
  }


  def healthCheck() = Action {
    Cached(1) {
      val serviceOk = tests.forall { test =>
        val result = test.result()
        if (!result) Logger.warn(s"${test.name} test failed, health check will fail")
        result
      }

      if (serviceOk)
        Ok(Json.obj("status" -> "ok", "gitCommitId" -> app.BuildInfo.gitCommitId))
      else
        ServiceUnavailable("Service Unavailable")
    }
  }

}

