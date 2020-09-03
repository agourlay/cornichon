package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.{ Eq, Order, Show }
import com.github.agourlay.cornichon.core.{ CornichonError, ScenarioContext }

//experimental and unused ;)
abstract class GenericCollectionAssertStepBuilder[A: Show: Order: Eq: Diff] { outer =>

  protected val baseTitle: String
  protected def sessionExtractor(sc: ScenarioContext): Either[CornichonError, Iterable[A]]

  def size: GenericAssertStepBuilder[Int] = new GenericAssertStepBuilder[Int] {
    protected val baseTitle: String = s"$baseTitle's size"
    protected def sessionExtractor(sc: ScenarioContext): Either[CornichonError, (Int, Option[() => String])] =
      outer.sessionExtractor(sc).map(c => (c.size, None))
  }

  def isNotEmpty: AssertStep = {
    val fullTitle = s"$baseTitle is not empty"
    AssertStep(
      title = fullTitle,
      action = s => Assertion.either {
        sessionExtractor(s).map { asserted =>
          CollectionNotEmptyAssertion(asserted, baseTitle)
        }
      }
    )
  }

  def isEmpty: AssertStep = {
    val fullTitle = s"$baseTitle is empty"
    AssertStep(
      title = fullTitle,
      action = s => Assertion.either {
        sessionExtractor(s).map { asserted =>
          CollectionEmptyAssertion(asserted, baseTitle)
        }
      }
    )
  }

}

