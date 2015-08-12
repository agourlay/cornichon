package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.core.Feature.FeatureDef

trait Feature extends CornichonLogger {

  val resolver = new Resolver
  val engine = new Engine(resolver)
  private val session = Session.newSession

  val feat: FeatureDef

  def beforeFeature(featureName: String): Unit = ()
  def afterFeature(featureName: String): Unit = ()

  def beforeEachScenario(): Seq[Step[_]] = Seq.empty
  def afterEachScenario(): Seq[Step[_]] = Seq.empty

  def runFeature(): FeatureReport = {
    beforeFeature(feat.name)
    val scenarioReports = feat.scenarios.map { s ⇒
      log.info(s"Scenario : ${s.name}")
      val completeScenario = s.copy(steps = beforeEachScenario() ++ s.steps ++ afterEachScenario())
      engine.runScenario(completeScenario)(session)
    }
    val failedReports = scenarioReports.collect { case f: FailedScenarioReport ⇒ f }
    val successReports = scenarioReports.collect { case s: SuccessScenarioReport ⇒ s }
    if (failedReports.isEmpty)
      SuccessFeatureReport(successReports)
    else
      FailedFeatureReport(failedReports, failedReports.map(failedFeatureErrorMsg))
  }

  def failedFeatureErrorMsg(r: FailedScenarioReport): String =
    s"""
       |
       |Scenario "${r.scenarioName}" failed at step "${r.failedStep.step} with error:
       |${r.failedStep.error.msg}
       | """.trim.stripMargin

}

object Feature {
  case class FeatureDef(name: String, scenarios: Seq[Scenario])
}