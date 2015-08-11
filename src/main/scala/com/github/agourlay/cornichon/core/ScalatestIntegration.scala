package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }
import org.slf4j.LoggerFactory

trait ScalatestIntegration extends WordSpec with Matchers {
  this: Feature ⇒

  val log = LoggerFactory.getLogger("Cornichon")

  feat.name must {
    "pass all scenarios" in {
      val featureExecution = runFeature()
      featureExecution match {
        case s: SuccessFeatureReport ⇒
          s.scenariosResult.foreach(logSuccessScenario)
          assert(true)
        case f: FailedFeatureReport ⇒
          f.scenarioReport.foreach {
            case s: SuccessScenarioReport ⇒ logSuccessScenario(s)
            case f: FailedScenarioReport  ⇒ logFailedScenario(f)
          }
          val msg: String = f.scenarioReport.collect { case f: FailedScenarioReport ⇒ f }.map(failedFeatureErrorMsg).mkString(" ")
          fail(msg)
      }
    }
  }

  private def logFailedScenario(scenario: FailedScenarioReport): Unit = {
    log.error(s"Scenario ${scenario.scenarioName}")
    scenario.successSteps foreach { step ⇒
      log.info(s"   $step")
    }
    log.error(s"   ${scenario.failedStep.step}")
  }

  private def logSuccessScenario(scenario: SuccessScenarioReport): Unit = {
    log.info(s"Scenario ${scenario.scenarioName}")
    scenario.successSteps foreach { step ⇒
      log.info(s"   $step")
    }
  }

}
