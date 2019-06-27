package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.BaseFeature
import com.github.agourlay.cornichon.matchers.MatcherResolver
import monix.eval.Task
import monix.reactive.Observable

case class FeatureRunner(featureDef: FeatureDef, baseFeature: BaseFeature, explicitSeed: Option[Long]) {

  private val featureContext = FeatureContext(
    beforeSteps = baseFeature.beforeEachScenario.toList,
    finallySteps = baseFeature.afterEachScenario.toList,
    featureIgnored = featureDef.ignored.isDefined,
    focusedScenarios = featureDef.focusedScenarios,
    withSeed = explicitSeed.orElse(baseFeature.seed),
    customExtractors = baseFeature.registerExtractors,
    allMatchers = (MatcherResolver.builtInMatchers ::: baseFeature.registerMatchers).groupBy(_.key)
  )

  final def runScenario(s: Scenario): Task[ScenarioReport] = {
    println(s"Starting scenario '${s.name}'")
    ScenarioRunner.runScenario(Session.newEmpty, featureContext)(s)
  }

  final def runFeature(filterScenario: Scenario ⇒ Boolean)(scenarioResultHandler: ScenarioReport ⇒ ScenarioReport): Task[List[ScenarioReport]] = {
    val scenariosToRun = featureDef.scenarios.filter(filterScenario)
    if (scenariosToRun.isEmpty)
      FeatureRunner.noop
    else {
      // Run 'before feature' hooks
      baseFeature.beforeFeature.foreach(f ⇒ f())
      // parallelism is limited to avoid spawning too much work at once
      val parallelism = if (baseFeature.executeScenariosInParallel) Math.min(scenariosToRun.size, FeatureRunner.coreNum) else 1
      Observable.fromIterable(scenariosToRun)
        .mapParallelUnordered(parallelism)(runScenario(_).map(scenarioResultHandler))
        .toListL
        .map { results ⇒
          // Run 'after feature' hooks
          baseFeature.afterFeature.foreach(f ⇒ f())
          results
        }
    }
  }

}

object FeatureRunner {
  lazy val coreNum: Int = Runtime.getRuntime.availableProcessors()
  private val noop = Task.now(Nil)
}
