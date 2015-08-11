package com.github.agourlay.cornichon.core

import cats.data.Xor
import com.github.agourlay.cornichon.core.Feature.FeatureDef

trait Feature {

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
      val completeScenario = s.copy(steps = beforeEachScenario() ++ s.steps ++ afterEachScenario())
      val report = engine.runScenario(completeScenario)(session)
      report
    }
    val (failedReport, successReport) = scenarioReports.partition(_.isLeft)
    if (failedReport.isEmpty)
      SuccessFeatureReport(successReport.collect { case Xor.Right(sr) ⇒ sr })
    else
      FailedFeatureReport(scenarioReports.map(_.fold(identity, identity)))
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