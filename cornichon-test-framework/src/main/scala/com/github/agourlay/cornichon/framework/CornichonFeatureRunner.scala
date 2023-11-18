package com.github.agourlay.cornichon.framework

import cats.syntax.either._
import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.BaseFeature
import sbt.testing.{ Event, EventHandler, Fingerprint, OptionalThrowable, Selector, Status, TestSelector }

import scala.concurrent.duration.Duration

object CornichonFeatureRunner {
  def loadAndExecute(featureInfo: FeatureInfo, eventHandler: EventHandler, seed: Option[Long], scenarioNameFilter: Set[String]): IO[Boolean] = {
    val baseFeature = featureInfo.featureClass.getConstructor().newInstance().asInstanceOf[BaseFeature]

    // load feature
    val now = System.nanoTime()
    val feature = Either.catchNonFatal(baseFeature.feature)
    val loadingDuration = Some(Duration.fromNanos(System.nanoTime() - now))
    feature match {
      case Left(e) =>
        val msg = e match {
          case c: CornichonError => c.renderedMessage
          case e: Throwable      => e.getMessage
        }
        val banner =
          s"""
             |exception thrown during Feature initialization - $msg :
             |${CornichonError.genStacktrace(e)}
             |""".stripMargin
        println(FailureLogInstruction(banner, 0).colorized)
        eventHandler.handle(failureEventBuilder(featureInfo, e))
        IO.pure(false)
      case Right(feature) =>
        val (featureLog, featureRun) = feature.ignored match {
          case Some(reason) =>
            // Early detection of ignored feature to not generate logs for each scenario
            // This is not emitting the SBT `Status.Ignored` that counts tests.
            val msg = s"${feature.name}: ignored because $reason"
            val featureLog = WarningLogInstruction(msg, 0, loadingDuration).colorized
            (featureLog, IO.pure(true))
          case None =>
            val featureLog = SuccessLogInstruction(s"${feature.name}", 0, loadingDuration).colorized
            val featureRunner = FeatureRunner(feature, baseFeature, seed)
            val run = featureRunner.runFeature(filterScenarios(scenarioNameFilter))(generateResultEvent(featureInfo, eventHandler))
              .map { results =>
                results.foreach(printResultLogs(featureInfo.featureClass))
                results.forall(_.isSuccess)
              }.handleError { e =>
                val banner =
                  s"""
                     |exception thrown during Feature execution:
                     |${CornichonError.genStacktrace(e)}
                     |""".stripMargin
                println(FailureLogInstruction(banner, 0).colorized)
                eventHandler.handle(failureEventBuilder(featureInfo, e))
                false
              }
            (featureLog, run)
        }
        println(featureLog)
        featureRun
    }
  }

  private def filterScenarios(scenarioNameFilter: Set[String])(s: Scenario): Boolean =
    if (scenarioNameFilter.isEmpty)
      true
    else
      scenarioNameFilter.contains(s.name)

  private def replayCommand(featureClass: Class[_], scenarioName: String, seed: Long): String =
    s"""testOnly *${featureClass.getSimpleName} -- "$scenarioName" "--seed=$seed""""

  private def generateResultEvent(featureInfo: FeatureInfo, eventHandler: EventHandler)(sr: ScenarioReport): ScenarioReport = {
    eventHandler.handle(eventBuilder(featureInfo, sr, sr.duration.toMillis))
    sr
  }

  private def printResultLogs(featureClass: Class[_])(sr: ScenarioReport): Unit = sr match {
    case s: SuccessScenarioReport =>
      val msg = s"- ${s.scenarioName} [${s.duration.toMillis} ms]"
      println(SuccessLogInstruction(msg, 0).colorized)
      if (s.shouldShowLogs) {
        LogInstruction.printLogs(s.logs)
        println(
          s"""|  ${fansi.Color.LightGray("replay only this scenario with the command:").overlay(attrs = fansi.Underlined.On).render}
              |  ${replayCommand(featureClass, s.scenarioName, s.seed)}
              |""".stripMargin
        )
      }

    case f: FailureScenarioReport =>
      val msg = failureErrorMessage(featureClass, f)
      println(FailureLogInstruction(msg, 0).colorized)
      println(f.renderedColoredLogs)

    case i: IgnoreScenarioReport =>
      val msg = s"- **ignored** ${i.scenarioName} (${i.reason}) "
      println(WarningLogInstruction(msg, 0).colorized)

    case p: PendingScenarioReport =>
      val msg = s"- **pending** ${p.scenarioName} "
      println(DebugLogInstruction(msg, 0).colorized)
  }

  private def failureErrorMessage(featureClass: Class[_], f: FailureScenarioReport): String =
    s"""|- **failed** ${f.scenarioName} [${f.duration.toMillis} ms]
        |
        |  ${f.msg.split('\n').iterator.mkString("\n  ")}
        |
        |  ${fansi.Color.Red("replay only this scenario with the command:").overlay(attrs = fansi.Underlined.On).render}
        |  ${replayCommand(featureClass, f.scenarioName, f.seed)}""".stripMargin

  private def eventBuilder(featureInfo: FeatureInfo, sr: ScenarioReport, durationInMillis: Long): Event = new Event {
    val status = sr match {
      case _: SuccessScenarioReport => Status.Success
      case _: FailureScenarioReport => Status.Failure
      case _: IgnoreScenarioReport  => Status.Ignored
      case _: PendingScenarioReport => Status.Pending
    }
    val throwable = sr match {
      case f: FailureScenarioReport =>
        val reporting = s"${f.msg}\n${f.renderedLogs}"
        new OptionalThrowable(CornichonException(reporting))
      case _ =>
        new OptionalThrowable()
    }
    val fullyQualifiedName = featureInfo.fullyQualifiedName
    val selector = new TestSelector(sr.scenarioName) // points to the correct scenario
    val fingerprint = featureInfo.fingerprint
    val duration = durationInMillis
  }

  private def failureEventBuilder(featureInfo: FeatureInfo, exception: Throwable): Event = new Event {
    val status = Status.Failure
    val throwable = new OptionalThrowable(exception)
    val fullyQualifiedName = featureInfo.fullyQualifiedName
    val selector = featureInfo.selector
    val fingerprint = featureInfo.fingerprint
    val duration = 0L
  }
}

case class FeatureInfo(fullyQualifiedName: String, featureClass: Class[_], fingerprint: Fingerprint, selector: Selector)