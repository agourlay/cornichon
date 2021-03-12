package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.SessionSteps.SessionArrayStepBuilder
import com.github.agourlay.cornichon.testHelpers.CommonTesting
import org.scalacheck.Prop.forAll
import org.scalacheck.{ Gen, Properties }
import org.typelevel.claimant.Claim

class SessionArrayStepBuilderProperties extends Properties("SessionArraySteps") with CommonTesting {
  private val testKey = "test-key"
  private val sessionArrayStepBuilder = SessionArrayStepBuilder(testKey)

  private val listAndReverseOrder = for {
    list <- Gen.nonEmptyListOf(Gen.alphaStr)
    reverse <- Gen.oneOf(true, false)
  } yield (list, reverse)

  property("SessionArraySteps containsExactly") =
    forAll(listAndReverseOrder) {
      case (list: List[String], reverse: Boolean) =>
        val inputs = if (reverse) list.reverse else list
        val session = inputs.foldLeft(Session.newEmpty) { case (s, input) => s.addValuesUnsafe(testKey -> input) }
        val step = sessionArrayStepBuilder.containsExactly(list: _*)
        val s = Scenario("scenario with SessionArraySteps", step :: Nil)
        val t = awaitTask(ScenarioRunner.runScenario(session)(s))
        Claim(t.isSuccess)
    }

}
