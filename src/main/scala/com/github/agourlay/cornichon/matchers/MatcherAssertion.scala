package com.github.agourlay.cornichon.matchers

import cats.data.Validated.{ invalidNel, valid }
import cats.data.{ NonEmptyList, ValidatedNel }
import com.github.agourlay.cornichon.steps.regular.assertStep.Assertion
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import io.circe.Json

trait MatcherAssertion extends Assertion {
  def matcherKey: String
  def input: String
  def assertionPredicate(input: String): Boolean

  override def validated =
    Either.catchNonFatal(assertionPredicate(input))
      .leftMap(e ⇒ MatcherAssertionEvaluationError(matcherKey, input, e))
      .fold[ValidatedNel[CornichonError, Done]](
        errors ⇒ invalidNel(errors),
        booleanResult ⇒ if (booleanResult) valid(Done) else invalidNel(MatcherAssertionError(matcherKey, input))
      )
}

case class MatcherAssertionEvaluationError(matcherKey: String, input: String, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"evaluation of matcher '$matcherKey' failed for input $input"
  override val causedBy = Some(NonEmptyList.of(CornichonError.fromThrowable(error)))
}

case class MatcherAssertionError(matcherKey: String, input: String) extends CornichonError {
  val baseErrorMessage = s"matcher '$matcherKey' did not match input '$input'"
}

object MatcherAssertion {
  def atJsonPath(jsonPath: JsonPath, json: Json, matcher: Matcher) = {
    new MatcherAssertion {
      def input = CornichonJson.jsonStringValue(jsonPath.run(json))

      def assertionPredicate(input: String) = matcher.predicate(input)

      def matcherKey = matcher.key
    }
  }
}
