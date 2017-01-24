package com.github.agourlay.cornichon.matchers

import cats.data.Validated.{ invalidNel, valid }
import cats.data.ValidatedNel
import com.github.agourlay.cornichon.steps.regular.assertStep.Assertion
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import io.circe.Json

trait MatcherAssertion extends Assertion {
  def matcheKey: String
  def input: String
  def assertionPredicate(input: String): Boolean

  override def validated = {
    Either.catchNonFatal(assertionPredicate(input))
      .leftMap(e ⇒ MatcherAssertionEvaluationError(matcheKey, input, e))
      .fold[ValidatedNel[CornichonError, Done]](
        errors ⇒ invalidNel(errors),
        booleanResult ⇒ if (booleanResult) valid(Done) else invalidNel(MatcherAssertionError(matcheKey, input))
      )
  }
}

case class MatcherAssertionEvaluationError(matcherKey: String, input: String, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"error '${error.getMessage}' thrown during evaluation of matcher '$matcherKey' for input $input"
}

case class MatcherAssertionError(matcherKey: String, input: String) extends CornichonError {
  val baseErrorMessage = s"matcher '$matcherKey' did not match input '$input'"
}

object MatcherAssertion {
  def atJsonPath(jsonPath: JsonPath, json: Json, matcher: Matcher) = {
    new MatcherAssertion {
      def input = CornichonJson.jsonStringValue(jsonPath.run(json))

      def assertionPredicate(input: String) = matcher.predicate(input)

      def matcheKey = matcher.key
    }
  }
}
