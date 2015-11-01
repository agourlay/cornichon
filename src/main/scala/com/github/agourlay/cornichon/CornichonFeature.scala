package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.client.AkkaHttpClient
import com.github.agourlay.cornichon.http.{ CornichonJson, HttpService }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait CornichonFeature extends HttpDsl with ScalaTestIntegration {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  lazy val baseUrl = ""
  lazy val requestTimeout = 2000 millis
  lazy val http = new HttpService(baseUrl, requestTimeout, AkkaHttpClient.default, new Resolver, new CornichonJson)

  private val engine = new Engine()

  protected var beforeFeature: Seq[() ⇒ Unit] = Nil
  protected var afterFeature: Seq[() ⇒ Unit] = Nil

  protected var beforeEachScenario: Seq[Step] = Nil
  protected var afterEachScenario: Seq[Step] = Nil

  def runScenario(s: Scenario): ScenarioReport = {
    val completeScenario = s.copy(steps = beforeEachScenario.toVector ++ s.steps ++ afterEachScenario)
    engine.runScenario(completeScenario)(Session.newSession)
  }

  // TODO switch to val
  def feature: FeatureDef

  def beforeFeature(before: ⇒ Unit): Unit =
    beforeFeature = beforeFeature :+ (() ⇒ before)

  def afterFeature(after: ⇒ Unit): Unit =
    afterFeature = (() ⇒ after) +: afterFeature

  def beforeEachScenario(steps: Seq[Step]): Unit =
    beforeEachScenario = beforeEachScenario ++ steps

  def afterEachScenario(steps: Seq[Step]): Unit =
    afterEachScenario = steps ++ afterEachScenario
}
