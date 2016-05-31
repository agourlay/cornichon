package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

sealed trait StepsReport {
  def logs: Vector[LogInstruction]
  def session: Session
  def isSuccess: Boolean
  def merge(otherStepsReport: StepsReport): StepsReport
}

case class SuccessRunSteps(session: Session, logs: Vector[LogInstruction]) extends StepsReport {
  val isSuccess = true

  def merge(otherStepRunReport: StepsReport) = otherStepRunReport match {
    case s: SuccessRunSteps ⇒
      // Success + Sucess = Success
      SuccessRunSteps(session.merge(otherStepRunReport.session), logs ++ otherStepRunReport.logs)
    case f: FailedRunSteps ⇒
      // Success + Error = Error
      f.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
  }

}
case class FailedRunSteps(step: Step, error: CornichonError, logs: Vector[LogInstruction], session: Session) extends StepsReport {
  val isSuccess = false

  def merge(otherStepRunReport: StepsReport) = otherStepRunReport match {
    case s: SuccessRunSteps ⇒
      // Error + Success = Error
      this.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
    case f: FailedRunSteps ⇒
      // Error + Error = Error
      f.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
  }
}

object FailedRunSteps {
  def apply(step: Step, error: Throwable, logs: Vector[LogInstruction], session: Session): FailedRunSteps = {
    val e = CornichonError.fromThrowable(error)
    FailedRunSteps(step, e, logs, session)
  }
}

case class ScenarioReport(scenarioName: String, stepsRunReport: StepsReport) {

  val msg = stepsRunReport match {
    case s: SuccessRunSteps ⇒
      s"Scenario '$scenarioName' succeeded"

    case FailedRunSteps(failedStep, error, _, _) ⇒
      s"""
      |
      |Scenario '$scenarioName' failed at step:
      |${failedStep.title}
      |with error:
      |${error.msg}
      | """.trim.stripMargin
  }
}

sealed trait LogInstruction {
  def message: String
  def marginNb: Int
  def duration: Option[Duration]
  def colorized: String
  val physicalMargin = "   "
  val completeMessage = {

    def withDuration(line: String) = physicalMargin * marginNb + line + duration.fold("")(d ⇒ s" (${d.toMillis} millis)")

    // Inject duration at the end of the first line
    message.split('\n').toList match {
      case head :: Nil ⇒
        withDuration(head)
      case head :: tail ⇒
        (withDuration(head) :: tail.map(l ⇒ physicalMargin * marginNb + l)).mkString("\n")
      case _ ⇒ withDuration("")
    }
  }
}

case class ScenarioTitleLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  val colorized = '\n' + fansi.Color.White(completeMessage).overlay(attrs = fansi.Underlined.On, start = (physicalMargin * marginNb).length).render
}

case class InfoLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  val colorized = fansi.Color.White(completeMessage).render
}

case class SuccessLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  val colorized = fansi.Color.Green(completeMessage).render
}

case class FailureLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  val colorized = fansi.Color.Red(completeMessage).render
}

case class DebugLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  val colorized = fansi.Color.Cyan(completeMessage).render
}