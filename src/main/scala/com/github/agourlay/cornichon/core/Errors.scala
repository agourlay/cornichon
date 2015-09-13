package com.github.agourlay.cornichon.core

import org.json4s._
import org.json4s.native.JsonMethods._

trait CornichonError extends Exception {
  val msg: String
}

case class StepExecutionError[A](title: String, exception: Throwable) extends CornichonError {
  val msg = s"step '$title' failed by throwing exception ${exception.printStackTrace()}"
}

case class StepAssertionError[A](expected: A, actual: A) extends CornichonError {
  private val baseMsg =
    s"""|expected result was:
        |'$expected'
        |but actual result is:
        |'$actual'
        |""".stripMargin.trim

  val msg = actual match {
    case s: String ⇒
      s"$baseMsg \n String diff is '${s.diff(expected.asInstanceOf[String])}'"
    case JString(s) ⇒
      s"$baseMsg \n String diff is '${s.diff(expected.asInstanceOf[JString].s)}'"
    case j: JValue ⇒
      val Diff(changed, added, deleted) = j diff expected.asInstanceOf[JValue]
      s"""|expected result was:
          |'${pretty(render(expected.asInstanceOf[JValue]))}'
          |but actual result is:
          |'${pretty(render(actual.asInstanceOf[JValue]))}'
          |diff:
          |${if (changed == JNothing) "" else "changed = " + pretty(render(changed))}
          |${if (added == JNothing) "" else "added = " + pretty(render(added))}
          |${if (deleted == JNothing) "" else "deleted = " + pretty(render(deleted))}
      """.stripMargin.trim
    case j: Seq[A] ⇒ s"$baseMsg \n Seq diff is '${j.diff(expected.asInstanceOf[Seq[A]])}'"
    case _         ⇒ baseMsg
  }
}

case class ResolverError(key: String) extends CornichonError {
  val msg = s"key '<$key>' can not be resolved"
}

case class KeyNotFoundInSession(key: String) extends CornichonError {
  val msg = s"key '$key' can not be found in session"
}

case class WhileListError(msg: String) extends CornichonError

case class NotAnArrayError[A](badPayload: A) extends CornichonError {
  val msg = s"Expected JSON Array but got $badPayload"
}

case class DataTableError(msg: String) extends CornichonError