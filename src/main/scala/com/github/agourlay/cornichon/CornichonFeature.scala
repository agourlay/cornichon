package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.HttpFeature

trait CornichonFeature extends ScalaTestIntegration with HttpDsl with HttpFeature {

  private val engine = new Engine()
  private val session = Session.newSession

  private def failedFeatureErrorMsg(r: FailedScenarioReport): String =
    s"""
       |
       |Scenario "${r.scenarioName}" failed at step "${r.failedStep.step} with error:
       |${r.failedStep.error.msg}
       | """.trim.stripMargin

  protected def runFeature(): FeatureReport = {
    val feat = feature
    val scenarioReports = try {
      beforeFeature()
      feat.scenarios.map { s ⇒
        logger.info(s"Scenario : ${s.name}")
        val completeScenario = s.copy(steps = beforeEachScenario() ++ s.steps ++ afterEachScenario())
        engine.runScenario(completeScenario)(session)
      }
    } finally
      afterFeature()

    val failedReports = scenarioReports.collect { case f: FailedScenarioReport ⇒ f }
    val successReports = scenarioReports.collect { case s: SuccessScenarioReport ⇒ s }
    if (failedReports.isEmpty)
      SuccessFeatureReport(feat.name, successReports)
    else
      FailedFeatureReport(feat.name, failedReports, failedReports.map(failedFeatureErrorMsg))
  }

  def feature: FeatureDef

  def beforeFeature(): Unit = ()
  def afterFeature(): Unit = ()

  def beforeEachScenario(): Seq[ExecutableStep[_]] = Seq.empty
  def afterEachScenario(): Seq[ExecutableStep[_]] = Seq.empty

}
