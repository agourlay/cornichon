package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.{ DataTableParser, HttpDsl }
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.http.HttpService

import org.json4s.JsonAST.JArray
import org.json4s.jackson.JsonMethods._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait CornichonFeature extends HttpDsl with ScalaTestIntegration {

  private val ec: ExecutionContext = ExecutionContext.global
  private val engine = new Engine(ec)

  protected var beforeFeature: Seq[() ⇒ Unit] = Nil
  protected var afterFeature: Seq[() ⇒ Unit] = Nil

  protected var beforeEachScenario: Seq[Step] = Nil
  protected var afterEachScenario: Seq[Step] = Nil

  lazy val http = new HttpService(baseUrl, requestTimeout, HttpClient.globalAkkaClient, resolver, ec)
  lazy val baseUrl = ""
  lazy val requestTimeout = 2000 millis
  lazy val resolver = new Resolver(registerExtractors)

  protected def runScenario(s: Scenario) =
    engine.runScenario(Session.newSession) {
      s.copy(steps = beforeEachScenario.toVector ++ s.steps ++ afterEachScenario)
    }

  def parseDataTable(table: String): JArray = {
    val sprayArray = DataTableParser.parseDataTable(table).asSprayJson
    JArray(sprayArray.elements.map(v ⇒ parse(v.toString())).toList)
  }

  def feature: FeatureDef

  def registerExtractors: Map[String, Mapper] = Map.empty

  def beforeFeature(before: ⇒ Unit): Unit =
    beforeFeature = beforeFeature :+ (() ⇒ before)

  def afterFeature(after: ⇒ Unit): Unit =
    afterFeature = (() ⇒ after) +: afterFeature

  def beforeEachScenario(steps: Seq[Step]): Unit =
    beforeEachScenario = beforeEachScenario ++ steps

  def afterEachScenario(steps: Seq[Step]): Unit =
    afterEachScenario = steps ++ afterEachScenario
}
