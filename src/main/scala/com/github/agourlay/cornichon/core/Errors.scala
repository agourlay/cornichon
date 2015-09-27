package com.github.agourlay.cornichon.core

import org.json4s._
import org.json4s.native.JsonMethods._

trait CornichonError extends Exception {
  val msg: String
}

case class StepExecutionError[A](exception: Throwable) extends CornichonError {
  val msg = s"exception thrown '${exception.getMessage}'"
}

case class StepAssertionError[A](expected: A, actual: A, negate: Boolean) extends CornichonError {
  private val baseMsg =
    s"""|expected result was${if (negate) " different than:" else ":"}
        |'$expected'
        |but actual result is:
        |'$actual'
        |""".stripMargin.trim

  val msg = actual match {
    case s: String ⇒ baseMsg
    case j: JValue ⇒
      val Diff(changed, added, deleted) = j diff expected.asInstanceOf[JValue]
      s"""|expected result was${if (negate) " different than:" else ":"}
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

case class MalformedHeadersError(error: String) extends CornichonError {
  val msg = s"error thrown while parsing headers $error"
}

case class ResolverError(key: String) extends CornichonError {
  val msg = s"key '<$key>' can not be resolved"
}

case class KeyNotFoundInSession(key: String, s: Session) extends CornichonError {
  val msg = s"key '$key' can not be found in session : \n${s.prettyPrint}"
}

case class WhileListError(msg: String) extends CornichonError

case class NotAnArrayError[A](badPayload: A) extends CornichonError {
  val msg = s"expected JSON Array but got $badPayload"
}

case class MalformedJsonError[A](input: A, exception: Throwable) extends CornichonError {
  val msg = s"malformed JSON input $input with ${exception.getMessage}"
}

case class DataTableError(msg: String) extends CornichonError