package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.HttpService

import scala.concurrent.duration._

trait CornichonFeature extends HttpDsl with ScalaTestIntegration {

  private val engine = new Engine()

  def runScenario(s: Scenario): ScenarioReport = {
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
