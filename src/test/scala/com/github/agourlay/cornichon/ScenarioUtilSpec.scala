package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.{ FailedFeatureReport, FeatureReport }
import com.github.agourlay.cornichon.server.RestAPI
import scala.concurrent.duration._

import scala.concurrent.Await

trait ScenarioUtilSpec {

  def printlnFailedScenario(res: FeatureReport): Unit = {
    if (!res.success)
      res match {
        case FailedFeatureReport(s, f) ⇒
          f.foreach { r ⇒ println("Failed Step " + r.failedStep) }
      }
  }

  def startTestDataHttpServer(port: Int) =
    Await.result(new RestAPI().start(port), 5 second)
}
