package com.github.agourlay.cornichon.core

import cats.data.Xor

trait Feature {

  val resolver = new Resolver
  val engine = new Engine(resolver)
  private val session = Session.newSession

  def featureName: String
  def scenarios: Seq[Scenario]

  def runFeature(): FeatureReport = {
    val scenarioReports = scenarios.map(engine.runScenario(_)(session))
    val (failedReport, successReport) = scenarioReports.partition(_.isLeft)
    if (failedReport.isEmpty)
      SuccessFeatureReport(successReport.collect { case Xor.Right(sr) ⇒ sr })
    else
      FailedFeatureReport(successReport.collect { case Xor.Right(sr) ⇒ sr }, failedReport.collect { case Xor.Left(fr) ⇒ fr })
  }
}