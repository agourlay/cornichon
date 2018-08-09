package com.github.agourlay.cornichon.core

import scala.collection.immutable.StringOps
import scala.concurrent.duration.Duration

sealed trait LogInstruction {
  def message: String
  def marginNb: Int
  def duration: Option[Duration]
  def colorized: String
  lazy val fullMargin: String = LogInstruction.physicalMargin * marginNb
  lazy val completeMessage: String = {

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
  val physicalMargin: StringOps = "   "
  def renderLogs(logs: Seq[LogInstruction], colorized: Boolean = true): String = {
    // Logs can potentially be really long - enable imperative mode
    val b = StringBuilder.newBuilder
    var i = 0
    val logNb = logs.size
    while (i < logNb) {
      val l = logs(i)
      b.append("\n").append(if (colorized) l.colorized else l.completeMessage)
      i += 1
    }
    b.append("\n").result()
  }

  def printLogs(logs: Seq[LogInstruction]): Unit =
    println(renderLogs(logs))

}

case class ScenarioTitleLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.White(completeMessage).overlay(attrs = fansi.Underlined.On, start = fullMargin.length).render
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