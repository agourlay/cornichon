package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

sealed trait LogInstruction {
  def message: String
  def marginNb: Int
  def duration: Option[Duration]
  def colorized: String
  lazy val completeMessage: String = {

    val fullMargin = LogInstruction.physicalMargin * marginNb
    def withDuration(line: String) = fullMargin + line + duration.fold("")(d ⇒ s" (${d.toMillis} millis)")

    // Inject duration at the end of the first line
    message.split('\n').toList match {
      case head :: Nil ⇒
        withDuration(head)
      case head :: tail ⇒
        (withDuration(head) :: tail.map(l ⇒ fullMargin + l)).mkString("\n")
      case _ ⇒ withDuration("")
    }
  }
}

object LogInstruction {
  val physicalMargin = "   "
  def printLogs(logs: Seq[LogInstruction]): Unit = {
    val acc = logs.foldLeft("")((acc, l) ⇒ acc + "\n" + l.colorized)
    println(acc + "\n")
  }
}

case class ScenarioTitleLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.White(completeMessage).overlay(attrs = fansi.Underlined.On, start = (LogInstruction.physicalMargin * marginNb).length).render
}

case class InfoLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.DarkGray(completeMessage).render
}

case class SuccessLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.Green(completeMessage).render
}

case class WarningLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.Yellow(completeMessage).render
}

case class FailureLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.Red(completeMessage).render
}

case class DebugLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.Cyan(completeMessage).render
}