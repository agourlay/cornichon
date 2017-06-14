package com.github.agourlay.cornichon.scalatest

import com.github.agourlay.cornichon.core.CornichonError
import org.scalatest.Reporter
import org.scalatest.events._

// Can be used when running test without SBT to format progress for Teamcity
class TeamcityReporter extends Reporter {

  private def teamcityReport(messageName: String, attributes: (String, String)*): Unit = {
    val attributeString = attributes.map {
      case (k, v) ⇒ s"$k='${tidy(v)}'"
    }.mkString(" ")
    println(s"##teamcity[$messageName $attributeString]")
  }

  // http://confluence.jetbrains.net/display/TCD65/Build+Script+Interaction+with+TeamCity
  def tidy(s: String) = s
    .replace("|", "||")
    .replace("'", "|'")
    .replace("\n", "|n")
    .replace("\r", "|r")
    .replace("\u0085", "|x")
    .replace("\u2028", "|l")
    .replace("\u2029", "|p")
    .replace("[", "|[")
    .replace("]", "|]")

  def apply(event: Event): Unit = event match {
    case r: RunStarting ⇒
      teamcityReport("testCount", "count" → r.testCount.toString)
    case r: TestStarting ⇒
      teamcityReport("testStarted", "name" → r.testName)
    case r: TestSucceeded ⇒
      val attributes = r.duration.map(d ⇒ "duration" → d.toString).toList :+ ("name" → r.testName)
      teamcityReport("testFinished", attributes: _*)
    case r: TestFailed ⇒
      val attributes = r.throwable.map(t ⇒ "details" → CornichonError.genStacktrace(t)).toList ::: List("name" → r.testName, "message" → r.message)
      teamcityReport("testFailed", attributes: _*)
    case r: TestIgnored ⇒
      teamcityReport("testIgnored", "name" → r.testName)
    case r: TestPending ⇒
      teamcityReport("testPending", "name" → r.testName)
    case r: SuiteStarting ⇒
      teamcityReport("testSuiteStarted", "name" → r.suiteName)
    case r: SuiteCompleted ⇒
      teamcityReport("testSuiteFinished", "name" → r.suiteName)
    case r: SuiteAborted ⇒
      val attributes = r.throwable.map(t ⇒ List("errorDetails" → CornichonError.genStacktrace(t), "statusText" → "ERROR")).toList.flatten :+ ("name" → r.suiteName)
      teamcityReport("testSuiteAborted", attributes: _*)
    case r: RunAborted ⇒
      val attributes = r.throwable.map(t ⇒ List("errorDetails" → CornichonError.genStacktrace(t), "statusText" → "ERROR")).toList.flatten :+ ("message" → r.message)
      teamcityReport("testSuiteAborted", attributes: _*)

    case _ ⇒ ()
  }
}
