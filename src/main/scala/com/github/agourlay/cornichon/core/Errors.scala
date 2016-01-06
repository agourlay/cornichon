package com.github.agourlay.cornichon.core

import org.json4s._
import com.github.agourlay.cornichon.json.CornichonJson._

import scala.util.control.NoStackTrace

trait CornichonError extends Exception with NoStackTrace {
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
      s"""|expected result was${if (negate) " different than:" else ":"}
          |'${prettyPrint(expected.asInstanceOf[JValue])}'
          |but actual result is:
          |'${prettyPrint(actual.asInstanceOf[JValue])}'
          |diff:
          |${prettyDiff(j, expected.asInstanceOf[JValue])}
      """.stripMargin.trim
    case j: Seq[A] ⇒ s"$baseMsg \n Seq diff is '${j.diff(expected.asInstanceOf[Seq[A]])}'"
    case _         ⇒ baseMsg
  }
}

case class DetailedStepAssertionError[A](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val msg = detailedAssertion(result)
}

case class MalformedHeadersError(error: String) extends CornichonError {
  val msg = s"error thrown while parsing headers $error"
}

trait ResolverError extends CornichonError {
  val key: String
}

case class SimpleResolverError(key: String, s: Session) extends ResolverError {
  val msg = s"key '<$key>' can not be resolved in session : \n${s.prettyPrint}"
}

case class ExtractorResolverError(key: String, s: Session, e: Throwable) extends ResolverError {
  val msg = s"key '<$key>' can not be resolved in session : \n${s.prettyPrint} due to exception thrown ${e.getMessage}"
}

case class ResolverParsingError(error: Throwable) extends CornichonError {
  val msg = s"error thrown during resolver parsing ${error.getMessage}"
}

case class JsonPathParsingError(error: String) extends CornichonError {
  val msg = s"error thrown during JsonPath parsing : $error"
}

case class JsonPathError(error: Throwable) extends CornichonError {
  val msg = s"error thrown during JsonPath parsing ${error.getMessage}"
}

case class EmptyKeyException(s: Session) extends CornichonError {
  val msg = s"key value can not be empty - session is \n${s.prettyPrint}"
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

case class DataTableError(error: Throwable) extends CornichonError {
  val msg = s"error thrown while parsing data table ${error.getMessage}"
}

case class DataTableParseError(msg: String) extends CornichonError

case object MalformedConcurrentBlock extends CornichonError {
  val msg = "malformed concurrent block without closing 'ConcurrentlyStop'"
}

case object MalformedEventuallyBlock extends CornichonError {
  val msg = "malformed eventually block without closing 'EventuallyStop'"
}

case object EventuallyBlockSucceedAfterMaxDuration extends CornichonError {
  val msg = "eventually block succeeded after 'maxDuration'"
}