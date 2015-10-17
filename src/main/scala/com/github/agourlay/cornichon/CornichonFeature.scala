package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.HttpService

import scala.concurrent.duration._

trait CornichonFeature extends HttpDsl with ScalaTestIntegration {

  private val engine = new Engine()

  protected var beforeFeature: Seq[() ⇒ Unit] = Nil
  protected var afterFeature: Seq[() ⇒ Unit] = Nil

  protected var beforeEachScenario: Seq[Step] = Nil
  protected var afterEachScenario: Seq[Step] = Nil

  def runScenario(s: Scenario): ScenarioReport = {
    val completeScenario = s.copy(steps = beforeEachScenario ++ s.steps ++ afterEachScenario)
    engine.runScenario(completeScenario)(Session.newSession)
  }

  // TODO switch to val
  def feature: FeatureDef

  lazy val baseUrl = ""
  lazy val requestTimeout = 2000 millis

  def beforeFeature(before: ⇒ Unit): Unit =
    beforeFeature = beforeFeature :+ (() ⇒ before)

  def afterFeature(after: ⇒ Unit): Unit =
    afterFeature = afterFeature :+ (() ⇒ after)

  def beforeEachScenario(steps: Seq[Step]): Unit =
    beforeEachScenario = beforeEachScenario ++ steps

  def afterEachScenario(steps: Seq[Step]): Unit =
    afterEachScenario = afterEachScenario ++ steps

  lazy val http = new HttpService(baseUrl, requestTimeout)

}
