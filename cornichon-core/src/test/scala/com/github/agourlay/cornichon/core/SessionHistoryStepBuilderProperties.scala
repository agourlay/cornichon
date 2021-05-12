package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.SessionSteps.SessionHistoryStepBuilder
import com.github.agourlay.cornichon.testHelpers.CommonTesting
import org.scalacheck.Prop.forAll
import org.scalacheck.{ Gen, Properties }
import org.typelevel.claimant.Claim

class SessionHistoryStepBuilderProperties extends Properties("SessionHistorySteps") with CommonTesting {
  private val testKey = "test-key"
  private val sessionHistoryStepBuilder = SessionHistoryStepBuilder(testKey)

  private val listAndReverseOrder = for {
    list <- Gen.nonEmptyListOf(Gen.alphaStr)
    reverse <- Gen.oneOf(true, false)
  } yield (list, reverse)

  property("SessionHistorySteps containsExactly") =
    forAll(listAndReverseOrder) {
      case (list: List[String], reverse: Boolean) =>
        val inputs = if (reverse) list.reverse else list
        val session = inputs.foldLeft(Session.newEmpty) { case (s, input) => s.addValuesUnsafe(testKey -> input) }
        val step = sessionHistoryStepBuilder.containsExactly(list: _*)
        val s = Scenario("scenario with SessionHistorySteps", step :: Nil)
        val t = awaitTask(ScenarioRunner.runScenario(session)(s))
        Claim(t.isSuccess)
    }

}
