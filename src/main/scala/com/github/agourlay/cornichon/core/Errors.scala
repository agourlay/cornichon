package com.github.agourlay.cornichon.core

import java.io.{ PrintWriter, StringWriter }

import org.json4s._
import com.github.agourlay.cornichon.json.CornichonJson._

import scala.util.control.NoStackTrace

trait CornichonError extends Exception with NoStackTrace {
  val msg: String
}

object CornichonError {
  def genStacktrace(exception: Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    exception.printStackTrace(pw)
    sw.toString
  }
}

case class StepExecutionError[A](e: Throwable) extends CornichonError {
  val msg = s"exception thrown '${CornichonError.genStacktrace(e)}'"
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

case class ResolverParsingError(error: Throwable) extends CornichonError {
  val msg = s"error thrown during resolver parsing ${error.getMessage}"
}

case class EmptyKeyException(s: Session) extends CornichonError {
  val msg = s"key value can not be empty - session is \n${s.prettyPrint}"
}

case class KeyNotFoundInSession(key: String, s: Session) extends CornichonError {
  val msg = s"key '$key' can not be found in session : \n${s.prettyPrint}"
}

case class SimpleMapperError[A](key: String, e: Throwable) extends CornichonError {
  val msg = s"exception thrown in SimpleMapper '$key' : '${CornichonError.genStacktrace(e)}'"
}

case class GeneratorError(placeholder: String) extends CornichonError {
  val msg = s"generator mapped to placeholder '$placeholder' did not generate a value"
}