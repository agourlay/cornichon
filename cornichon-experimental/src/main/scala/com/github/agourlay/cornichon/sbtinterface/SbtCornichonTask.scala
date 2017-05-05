package com.github.agourlay.cornichon.sbtinterface

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.feature.BaseFeature
import sbt.testing._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future, Promise }

class SbtCornichonTask(task: TaskDef) extends Task {

  override def tags(): Array[String] = Array.empty
  def taskDef(): TaskDef = task

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val p = Promise[Unit]()
    execute(eventHandler, loggers, _ ⇒ p.success(()))
    Await.result(p.future, Duration.Inf)
    Array.empty
  }

  def execute(eventHandler: EventHandler, loggers: Array[Logger],
    continuation: (Array[Task]) ⇒ Unit): Unit = {

    implicit val ec = BaseFeature.globalRuntime._4

    val c = Class.forName(task.fullyQualifiedName())
    val cons = c.getConstructor()
    val baseFeature = cons.newInstance().asInstanceOf[BaseFeature]

    val featureName = baseFeature.feature.name
    println(SuccessLogInstruction(s"$featureName:", 0).colorized)

    val featRes = baseFeature.feature.scenarios.map { s ⇒
      val startTS = System.currentTimeMillis()
      BaseFeature.reserveGlobalRuntime()
      baseFeature.runScenario(s).map { r ⇒
        //Generate result event
        val endTS = System.currentTimeMillis()
        eventHandler.handle(eventBuilder(r, endTS - startTS))
        BaseFeature.releaseGlobalRuntime()
        r
      }
    }

    Future.sequence(featRes)
      .map(results ⇒ results.foreach(printResultLogs))
      .onComplete(_ ⇒ continuation(Array.empty))
  }

  def printResultLogs(sr: ScenarioReport) = sr match {
    case s: SuccessScenarioReport ⇒
      // In case of success, logs are only shown if the scenario contains DebugLogInstruction
      if (s.logs.collect { case d: DebugLogInstruction ⇒ d }.nonEmpty)
        LogInstruction.printLogs(s.logs)
      else {
        val msg = s"- should ${s.scenarioName} "
        println(SuccessLogInstruction(msg, 0).colorized)
      }
    case f: FailureScenarioReport ⇒
      LogInstruction.printLogs(f.logs)
    case i: IgnoreScenarioReport ⇒
      val msg = s"- **ignored** ${i.scenarioName} "
      println(WarningLogInstruction(msg, 0).colorized)
  }

  def eventBuilder(sr: ScenarioReport, durationInMillis: Long) = new Event {
    val status = sr match {
      case _: SuccessScenarioReport ⇒ Status.Success
      case _: FailureScenarioReport ⇒ Status.Failure
      case _: IgnoreScenarioReport  ⇒ Status.Ignored
    }
    val throwable = new OptionalThrowable()
    val fullyQualifiedName = task.fullyQualifiedName()
    val selector = task.selectors().head
    val fingerprint = task.fingerprint()
    val duration = durationInMillis
  }

}
