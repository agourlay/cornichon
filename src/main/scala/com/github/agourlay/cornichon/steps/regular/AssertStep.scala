package com.github.agourlay.cornichon.steps.regular

import cats.Show
import cats.syntax.show._
import cats.data.Xor
import cats.data.Xor._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.Json

import scala.concurrent.ExecutionContext

case class AssertStep[A](title: String, action: Session ⇒ Assertion[A], show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val session = initialRunState.session
    val res = Xor.catchNonFatal(action(session))
      .leftMap(CornichonError.fromThrowable)
      .flatMap(runStepPredicate(session))
    xorToStepReport(this, res, initialRunState, show)
  }

  def runStepPredicate(newSession: Session)(assertion: Assertion[A]): Xor[CornichonError, Session] =
    if (assertion.isSuccessful)
      right(newSession)
    else
      left(assertion.assertionError)

}

abstract class Assertion[A: Show] {
  val expected: A
  val actual: A
  val expectedEqualsActual = expected == actual
  val isSuccessful = {
    val succeedAsExpected = expectedEqualsActual && !negate
    val failedAsExpected = !expectedEqualsActual && negate

    succeedAsExpected || failedAsExpected
  }
  val negate: Boolean
  def assertionError: CornichonError
}

case class GenericAssertion[A: Show](expected: A, actual: A, negate: Boolean = false) extends Assertion[A] {
  lazy val assertionError = GenericAssertionError(expected, actual, negate)
}

case class GenericAssertionError[A: Show](expected: A, actual: A, negate: Boolean) extends CornichonError {
  private val baseMsg =
    s"""|expected result was${if (negate) " different than:" else ":"}
        |'${expected.show}'
        |but actual result is:
        |'${actual.show}'
        |""".stripMargin.trim

  // TODO Introduce Eq + Diff type classes to extract logic
  val msg = (expected, actual) match {
    case (expectedString: String, actualString: String) ⇒
      baseMsg

    case (expectedJson: Json, actualJson: Json) ⇒
      s"""|$baseMsg
          |
          |JSON diff. between actual result and expected result is :
          |${prettyDiff(actualJson, expectedJson)}
      """.stripMargin.trim

    case (expectedSeq: Seq[A], actualSeq: Seq[A]) ⇒
      s"""|$baseMsg
          |
          |Collection diff. between actual result and expected result is :
          |${actualSeq.diff(expectedSeq).mkString(", ")}
      """.stripMargin.trim

    case (expectedSet: Set[A], actualSet: Set[A]) ⇒
      s"""$baseMsg
         |
         |Set diff. between actual result and expected result is :
         |${actualSet.diff(expectedSet).mkString(", ")}
       """.stripMargin.trim

    case _ ⇒ baseMsg
  }
}

case class CustomMessageAssertion[A: Show](expected: A, actual: A, customMessage: A ⇒ String, negate: Boolean = false) extends Assertion[A] {
  lazy val assertionError = CustomMessageAssertionError(actual, customMessage)
}

case class CustomMessageAssertionError[A: Show](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val msg = detailedAssertion(result)
}
