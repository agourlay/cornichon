package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.CornichonFeature
import org.scalatest.{ ParallelTestExecution, WordSpecLike, BeforeAndAfterAll }

trait ScalaTestIntegration extends WordSpecLike with BeforeAndAfterAll with ParallelTestExecution {
  this: CornichonFeature ⇒

  override def beforeAll() = beforeFeature()
  override def afterAll() = afterFeature()

  feature.name should {

    feature.scenarios.foreach { s ⇒
      if (s.ignored)
        s.name ignore {}
      else
        s.name in {
          runScenario(s) match {
            case SuccessScenarioReport(scenarioName, successSteps: Seq[String]) ⇒
              assert(true)
            case f @ FailedScenarioReport(scenarioName, failedStep, successSteps, notExecutedStep) ⇒
              fail(f.msg)
          }
        }
    }
  }
}
