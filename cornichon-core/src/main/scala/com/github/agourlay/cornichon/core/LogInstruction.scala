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
    // Inject human readable duration at the end of the line
    def withMarginAndDuration(builder: StringBuilder, line: String): Unit = {
      builder.append(fullMargin)
      builder.append(line)
      duration.foreach { dur =>
        val inMillis = dur.toMillis
        builder.append(" [")
        if (inMillis == 0) {
          // duration is less than 1ms, show in micros
          builder.append(dur.toMicros)
          builder.append(" μs")
        } else if (dur.toSeconds >= 10) {
          // duration is more than 10s, show in seconds
          builder.append(dur.toSeconds)
          builder.append(" s")
        } else {
          // duration is between 1ms and 10s, show in millis
          builder.append(inMillis)
          builder.append(" ms")
        }
        builder.append("]")
      }
    }

    // Inject duration at the end of the first line
    val builder = new StringBuilder()
    val lines = message.split('\n')
    val linesLen = lines.length
    linesLen match {
      case 0 => withMarginAndDuration(builder, "") // message was empty
      case 1 => withMarginAndDuration(builder, lines.head)
      case _ =>
        // multi-line message
        withMarginAndDuration(builder, lines.head)
        // not zero by construction
        val tailLen = linesLen - 1
        // non-empty tail, add a newline
        builder.append("\n")
        var i = 0
        lines.tail.foreach { l =>
          builder.append(fullMargin)
          builder.append(l)
          if (i < tailLen - 1) {
            builder.append("\n")
          }
          i += 1
        }
    }
    builder.result()
  }
}

object LogInstruction {
  private val physicalMargin: StringOps = "   "

  def renderLogs(logs: List[LogInstruction], colorized: Boolean = true): String = {
    val builder = new StringBuilder()
    logs.foreach {
      case NoShowLogInstruction(_, _, _) => ()
      case l: LogInstruction             => builder.append("\n").append(if (colorized) l.colorized else l.completeMessage)
    }
    builder.append("\n")
    builder.result()
  }

  def printLogs(logs: List[LogInstruction]): Unit =
    println(renderLogs(logs))

}

case class ScenarioTitleLogInstruction(message: String, marginNb: Int, duration: Option[Duration] = None) extends LogInstruction {
  lazy val colorized = fansi.Color.LightGray(completeMessage).overlay(attrs = fansi.Underlined.On, start = fullMargin.length).render
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