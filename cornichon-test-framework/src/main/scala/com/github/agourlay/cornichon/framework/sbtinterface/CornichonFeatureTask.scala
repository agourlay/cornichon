package com.github.agourlay.cornichon.framework.sbtinterface

import cats.syntax.either._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.feature.{ BaseFeature, FeatureRunner }

import monix.execution.Scheduler.Implicits.global

import sbt.testing._

import scala.concurrent.duration.Duration
import scala.concurrent.Await

class CornichonFeatureTask(task: TaskDef, scenarioNameFilter: Set[String]) extends Task {

  override def tags(): Array[String] = Array.empty
  override def taskDef(): TaskDef = task

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    Await.result(loadAndExecuteFeature(eventHandler).runAsync, Duration.Inf)
    Array.empty
  }

  private def loadAndExecuteFeature(eventHandler: EventHandler) = {
    val featureClass = Class.forName(task.fullyQualifiedName())
    val baseFeature = featureClass.getConstructor().newInstance().asInstanceOf[BaseFeature]

    Either.catchNonFatal(baseFeature.feature).fold(
      e ⇒ {
        val msg = e match {
          case c: CornichonError ⇒ c.renderedMessage
          case e: Throwable      ⇒ e.getMessage
        }
        val banner =
          s"""
             |exception thrown during Feature initialization - $msg :
             |${CornichonError.genStacktrace(e)}
             |""".stripMargin
        println(FailureLogInstruction(banner, 0).colorized)
        eventHandler.handle(failureEventBuilder(e))
        Done.taskDone
      },
      feature ⇒ {
        println(SuccessLogInstruction(s"${feature.name}:", 0).colorized)
        FeatureRunner(feature, baseFeature)
          .runFeature(filterScenarios)(generateResultEvent(eventHandler))
          .map { results ⇒
            results.foreach(printResultLogs(featureClass))
            Done
          }
      }
    )
  }

  private def filterScenarios(s: Scenario): Boolean =
    if (scenarioNameFilter.isEmpty)
      true
    else
      scenarioNameFilter.contains(s.name)

  private def replayCommand(featureClass: Class[_], scenarioName: String): String =
    s"""testOnly *${featureClass.getSimpleName} -- "$scenarioName" """

  private def generateResultEvent(eventHandler: EventHandler)(sr: ScenarioReport) = {
    eventHandler.handle(eventBuilder(sr, sr.duration.toMillis))
    sr
  }

  private def printResultLogs(featureClass: Class[_])(sr: ScenarioReport): Unit = sr match {
    case s: SuccessScenarioReport ⇒
      val msg = s"- ${s.scenarioName} (${s.duration.toMillis} millis)"
      println(SuccessLogInstruction(msg, 0).colorized)
      if (s.shouldShowLogs) LogInstruction.printLogs(s.logs)

    case f: FailureScenarioReport ⇒
      val msg = failureErrorMessage(featureClass, f.scenarioName, f.msg, f.duration)
      println(FailureLogInstruction(msg, 0).colorized)
      LogInstruction.printLogs(f.logs)

    case i: IgnoreScenarioReport ⇒
      val msg = s"- **ignored** ${i.scenarioName} "
      println(WarningLogInstruction(msg, 0).colorized)

    case p: PendingScenarioReport ⇒
      val msg = s"- **pending** ${p.scenarioName} "
      println(DebugLogInstruction(msg, 0).colorized)
  }

  private def failureErrorMessage(featureClass: Class[_], scenarioName: String, errorMessage: String, duration: Duration): String =
    s"""|- **failed** $scenarioName (${duration.toMillis} millis)
        |
        |  ${errorMessage.split('\n').toList.mkString("\n  ")}
        |
        |  ${fansi.Color.Red("replay only this scenario with the command:").overlay(attrs = fansi.Underlined.On).render}
        |  ${replayCommand(featureClass, scenarioName)}""".stripMargin

  private def eventBuilder(sr: ScenarioReport, durationInMillis: Long) = new Event {
    val status = sr match {
      case _: SuccessScenarioReport ⇒ Status.Success
      case _: FailureScenarioReport ⇒ Status.Failure
      case _: IgnoreScenarioReport  ⇒ Status.Ignored
      case _: PendingScenarioReport ⇒ Status.Pending
    }
    val throwable = sr match {
      case f: FailureScenarioReport ⇒
        new OptionalThrowable(new RuntimeException(f.msg))
      case _ ⇒
        new OptionalThrowable()
    }
    val fullyQualifiedName = task.fullyQualifiedName()
    val selector = new TestSelector(sr.scenarioName) // points to the correct scenario
    val fingerprint = task.fingerprint()
    val duration = durationInMillis
  }

  private def failureEventBuilder(exception: Throwable) = new Event {
    val status = Status.Failure
    val throwable = new OptionalThrowable(exception)
    val fullyQualifiedName = task.fullyQualifiedName()
    val selector = task.selectors().head
    val fingerprint = task.fingerprint()
    val duration = 0L
  }
}