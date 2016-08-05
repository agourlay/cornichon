package com.github.agourlay.cornichon.core

import java.io.{ PrintWriter, StringWriter }

import scala.util.control.NoStackTrace

trait CornichonError extends Exception with NoStackTrace {
  def msg: String

  override def getMessage = msg
}

object CornichonError {
  def genStacktrace(exception: Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    exception.printStackTrace(pw)
    sw.toString
  }

  def fromThrowable(exception: Throwable): CornichonError = exception match {
    case ce: CornichonError ⇒ ce
    case _                  ⇒ StepExecutionError(exception)
  }
}

case class StepExecutionError[A](e: Throwable) extends CornichonError {
  val msg = s"exception thrown '${CornichonError.genStacktrace(e)}'"
}