package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.HttpService

import scala.concurrent.duration._

trait CornichonFeature extends HttpDsl with ScalaTestIntegration {

  private val engine = new Engine()

  private def failedFeatureErrorMsg(r: FailedScenarioReport): String =
    s"""
       |
       |Scenario "${r.scenarioName}" failed at step "${r.failedStep.step}" with error:
       |${r.failedStep.error.msg}
       | """.trim.stripMargin

  protected def runFeature(): FeatureReport = {
    val feat = feature
    val scenarioReports = try {
      beforeFeature()
      if (parallelExecution)
        feat.scenarios.par.map(runScenario).seq
      else
        feat.scenarios map runScenario
    } finally
      afterFeature()

    val failedReports = scenarioReports.collect { case f: FailedScenarioReport ⇒ f }
    val successReports = scenarioReports.collect { case s: SuccessScenarioReport ⇒ s }
    if (failedReports.isEmpty)
      SuccessFeatureReport(feat.name, successReports)
    else
      FailedFeatureReport(feat.name, failedReports, failedReports.map(failedFeatureErrorMsg))
  }

  private def runScenario(s: Scenario): ScenarioReport = {
    logger.info(s"Scenario : ${s.name}")
    val completeScenario = s.copy(steps = beforeEachScenario ++ s.steps ++ afterEachScenario)
    engine.runScenario(completeScenario)(Session.newSession)
  }

  // TODO switch to val
  def feature: FeatureDef

  val parallelExecution: Boolean = false
  lazy val baseUrl = ""
  lazy val requestTimeout = 2000 millis

  def beforeFeature(): Unit = ()
  def afterFeature(): Unit = ()

  val beforeEachScenario: Seq[ExecutableStep[_]] = Seq.empty
  val afterEachScenario: Seq[ExecutableStep[_]] = Seq.empty

  lazy val http = new HttpService(baseUrl, requestTimeout)

}
