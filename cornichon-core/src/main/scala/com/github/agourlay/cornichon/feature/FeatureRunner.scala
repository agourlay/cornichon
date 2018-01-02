package com.github.agourlay.cornichon.feature

import com.github.agourlay.cornichon.core._

import monix.eval.Task
import monix.reactive.Observable

case class FeatureRunner(featureDef: FeatureDef, baseFeature: BaseFeature) {

  private val engine = Engine.withStepTitleResolver(baseFeature.placeholderResolver)
  private val featureContext = FeatureExecutionContext(baseFeature.beforeEachScenario, baseFeature.afterEachScenario, featureDef.ignored, featureDef.focusedScenarios)

  final def runScenario(s: Scenario): Task[ScenarioReport] = {
    println(s"Starting scenario '${s.name}'")
    engine.runScenario(Session.newEmpty, featureContext)(s)
  }

  final def runFeature(filterScenario: Scenario ⇒ Boolean)(scenarioResultHandler: ScenarioReport ⇒ ScenarioReport): Task[List[ScenarioReport]] = {
    // Run 'before feature' hooks
    baseFeature.beforeFeature.foreach(f ⇒ f())
    val scenariosToRun = featureDef.scenarios.filter(filterScenario)
    val parallelism = if (baseFeature.executeScenariosInParallel) scenariosToRun.size else 1
    Observable.fromIterable(scenariosToRun)
      .mapAsync(parallelism)(runScenario(_).map(scenarioResultHandler))
      .toListL
      .map { results ⇒
        // Run 'after feature' hooks
        baseFeature.afterFeature.foreach(f ⇒ f())
        results
      }
  }

}
