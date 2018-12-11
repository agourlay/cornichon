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

    def withMarginAndDuration(line: String): String = {
      val d = duration match {
        case None                           ⇒ ""
        case Some(dur) if dur.toMillis == 0 ⇒ s" [${dur.toMicros} μs]"
        case Some(dur)                      ⇒ s" [${dur.toMillis} ms]"
      }
      fullMargin + line + d
    }

    // Inject duration at the end of the first line
    message.split('\n').toList match {
      case head :: Nil ⇒
        withMarginAndDuration(head)
      case head :: tail ⇒
        (withMarginAndDuration(head) :: tail.map(l ⇒ fullMargin + l)).mkString("\n")
    }
  }
}

object LogInstruction {
  val physicalMargin: StringOps = "   "

  def renderLogs(logs: List[LogInstruction], colorized: Boolean = true): String =
    logs.foldLeft(StringBuilder.newBuilder) { (b, l) ⇒
      l match {
        case NoShowLogInstruction(_, _, _) ⇒ b
        case l: LogInstruction             ⇒ b.append("\n").append(if (colorized) l.colorized else l.completeMessage)
      }
    }.append("\n").result()

  def printLogs(logs: List[LogInstruction]): Unit =
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

case class NoShowLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.Black(completeMessage).render
}