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

  def runFeature(): FeatureReport = {
    val feat = feature
    beforeFeature(feat.name)
    val scenarioReports = feat.scenarios.map { s ⇒
      log.info(s"Scenario : ${s.name}")
      val completeScenario = s.copy(steps = beforeEachScenario() ++ s.steps ++ afterEachScenario())
      engine.runScenario(completeScenario)(session)
    }
    val failedReports = scenarioReports.collect { case f: FailedScenarioReport ⇒ f }
    val successReports = scenarioReports.collect { case s: SuccessScenarioReport ⇒ s }
    if (failedReports.isEmpty)
      SuccessFeatureReport(feat.name, successReports)
    else
      FailedFeatureReport(feat.name, failedReports, failedReports.map(failedFeatureErrorMsg))
  }

  def feature: FeatureDef

  def beforeFeature(featureName: String): Unit = ()
  def afterFeature(featureName: String): Unit = ()

  def beforeEachScenario(): Seq[Step[_]] = Seq.empty
  def afterEachScenario(): Seq[Step[_]] = Seq.empty

}
