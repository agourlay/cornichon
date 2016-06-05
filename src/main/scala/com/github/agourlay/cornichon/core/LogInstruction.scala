package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

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