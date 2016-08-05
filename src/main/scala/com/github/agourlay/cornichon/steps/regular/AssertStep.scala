package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import cats.data.Xor._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.Json

import scala.concurrent.ExecutionContext

case class AssertStep[A](
    title: String,
    action: Session ⇒ Assertion[A],
    negate: Boolean = false,
    show: Boolean = true
) extends Step {

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val session = initialRunState.session
    val res = Xor.catchNonFatal(action(session))
      .leftMap(CornichonError.fromThrowable)
      .flatMap { assertion ⇒
        runStepPredicate(session, assertion)
      }
    xorToStepReport(this, res, title, initialRunState, show)
  }

  //TODO think about making StepAssertion concrete implem. custom as well
  def runStepPredicate(newSession: Session, stepAssertion: Assertion[A]): Xor[CornichonError, Session] = {
    val succeedAsExpected = stepAssertion.isSuccess && !negate
    val failedAsExpected = !stepAssertion.isSuccess && negate

    if (succeedAsExpected || failedAsExpected) right(newSession)
    else
      stepAssertion match {
        case SimpleAssertion(expected, actual) ⇒
          left(AssertionError(expected, actual, negate))
        case DetailedAssertion(expected, actual, details) ⇒
          left(DetailedAssertionError(actual, details))
      }
  }
}

sealed trait Assertion[A] {
  def isSuccess: Boolean
}

case class SimpleAssertion[A](expected: A, result: A) extends Assertion[A] {
  val isSuccess = expected == result
}

case class DetailedAssertion[A](expected: A, result: A, details: A ⇒ String) extends Assertion[A] {
  val isSuccess = expected == result
}

case class AssertionError[A](expected: A, actual: A, negate: Boolean) extends CornichonError {
  private val baseMsg =
    s"""|expected result was${if (negate) " different than:" else ":"}
        |'$expected'
        |but actual result is:
        |'$actual'
        |""".stripMargin.trim

  // TODO Introduce Show + Diff (Eq?) type classes to remove casting and display objects nicely
  val msg = actual match {
    case s: String ⇒ baseMsg
    case j: Json ⇒
      s"""|expected result was${if (negate) " different than:" else ":"}
          |${prettyPrint(expected.asInstanceOf[Json])}
          |but actual result is:
          |${prettyPrint(actual.asInstanceOf[Json])}
          |
          |diff. between actual result and expected result is :
          |${prettyDiff(j, expected.asInstanceOf[Json])}
      """.stripMargin.trim
    case j: Seq[A] ⇒ s"$baseMsg \n Seq diff is '${j.diff(expected.asInstanceOf[Seq[A]]).mkString(", ")}'"
    case j: Set[A] ⇒ s"$baseMsg \n Set diff is '${j.diff(expected.asInstanceOf[Set[A]]).mkString(", ")}'"
    case _         ⇒ baseMsg
  }
}

case class DetailedAssertionError[A](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val msg = detailedAssertion(result)
}
