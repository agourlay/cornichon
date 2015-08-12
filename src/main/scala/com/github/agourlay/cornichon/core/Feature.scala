package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.core.Feature.FeatureDef
import org.slf4j.LoggerFactory

trait Feature {

  val log = LoggerFactory.getLogger("Cornichon")

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
      report match {
        case s: SuccessScenarioReport ⇒ logSuccessScenario(s)
        case f: FailedScenarioReport  ⇒ logFailedScenario(f)
      }
      report
    }
    val failedReports = scenarioReports.collect { case f: FailedScenarioReport ⇒ f }
    val successReports = scenarioReports.collect { case s: SuccessScenarioReport ⇒ s }
    if (failedReports.isEmpty)
      SuccessFeatureReport(successReports)
    else {
      val errors = failedReports.map(failedFeatureErrorMsg)
      FailedFeatureReport(failedReports, errors)
    }
  }

  def logFailedScenario(scenario: FailedScenarioReport): Unit = {
    log.error(s"Scenario ${scenario.scenarioName}")
    scenario.successSteps foreach { step ⇒
      log.info(s"   $step")
    }
    log.error(s"   ${scenario.failedStep.step}")
  }

  def logSuccessScenario(scenario: SuccessScenarioReport): Unit = {
    log.info(s"Scenario ${scenario.scenarioName}")
    scenario.successSteps foreach { step ⇒
      log.info(s"   $step")
    }
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