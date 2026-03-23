package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.SessionSteps.SessionHistoryStepBuilder
import com.github.agourlay.cornichon.testHelpers.CommonTesting
import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}

class SessionHistoryStepBuilderProperties extends Properties("SessionHistorySteps") with CommonTesting {
  private val testKey = "test-key"
  private val sessionHistoryStepBuilder = SessionHistoryStepBuilder(testKey)

  private val listAndReverseOrder = for {
    list <- Gen.nonEmptyListOf(Gen.alphaStr)
    reverse <- Gen.oneOf(true, false)
  } yield (list, reverse)

  property("containsExactly matches regardless of insertion order") = forAll(listAndReverseOrder) { case (list: List[String], reverse: Boolean) =>
    val inputs = if (reverse) list.reverse else list
    val session = inputs.foldLeft(Session.newEmpty) { case (s, input) => s.addValuesUnsafe(testKey -> input) }
    val step = sessionHistoryStepBuilder.containsExactly(list: _*)
    val s = Scenario("scenario with SessionHistorySteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("containsExactly succeeds for single element") = forAll(Gen.alphaStr) { value =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> value)
    val step = sessionHistoryStepBuilder.containsExactly(value)
    val s = Scenario("scenario with single element", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("containsExactly fails when history has extra values") = forAll(Gen.alphaStr, Gen.alphaStr) { (v1, v2) =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> v1, testKey -> v2)
    val step = sessionHistoryStepBuilder.containsExactly(v1) // missing v2
    val s = Scenario("scenario with missing value", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    !t.isSuccess
  }

  property("containsExactly preserves duplicate values") = forAll(Gen.alphaStr) { value =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> value, testKey -> value)
    val step = sessionHistoryStepBuilder.containsExactly(value, value)
    val s = Scenario("scenario with duplicates", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

}
