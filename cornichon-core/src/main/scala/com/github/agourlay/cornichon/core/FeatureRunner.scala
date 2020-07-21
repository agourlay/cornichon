package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.BaseFeature
import com.github.agourlay.cornichon.matchers.MatcherResolver
import monix.eval.Task
import monix.reactive.Observable
import cats.instances.list._
import cats.instances.either._
import cats.syntax.traverse._

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

  final def runFeature(filterScenario: Scenario => Boolean)(scenarioResultHandler: ScenarioReport => ScenarioReport): Task[List[ScenarioReport]] = {
    val scenariosToRun = featureDef.scenarios.filter(filterScenario)
    if (scenariosToRun.isEmpty)
      FeatureRunner.noop
    else {
      // Run 'before feature' hooks
      runBeforeFeature() match {
        // There was an error after a successful run, try running `afterFeature` hook to possibly clean things up
        case Left((beforeFeatureError, successRun)) if successRun >= 1 =>
          println("`beforeFeature` failed partially, let's try to run `afterFeature` to possibly clean things up")
          runAfterFeature() match {
            case Left(afterFeatureError) => Task.raiseError(HooksFeatureError(beforeFeatureError, afterFeatureError).toException)
            case Right(_)                => Task.raiseError(beforeFeatureError.toException)
          }
        // Failed completely, nothing to cleanup, fast exit
        case Left((beforeFeatureError, _)) => Task.raiseError(beforeFeatureError.toException)
        case Right(_) =>
          // featureParallelism is limited to avoid spawning too much work at once
          val featureParallelism = if (baseFeature.executeScenariosInParallel) {
            baseFeature.config.scenarioExecutionParallelismFactor * FeatureRunner.availaibleProcessors + 1
          } else 1
          Observable.fromIterable(scenariosToRun)
            .mapParallelUnordered(featureParallelism)(runScenario(_).map(scenarioResultHandler))
            .toListL
            .flatMap { results =>
              // Run 'after feature' hooks
              runAfterFeature() match {
                case Left(afterFeatureError) => Task.raiseError(afterFeatureError.toException)
                case Right(_)                => Task.now(results)
              }
            }
      }
    }
  }

  // Returns the number of successful run before the error.
  private def runBeforeFeature(): Either[(CornichonError, Int), Done] = {
    var successRun = 0
    var error = Option.empty[CornichonError]
    val it = baseFeature.beforeFeature.toList.iterator
    while (it.hasNext && error.isEmpty) {
      val f = it.next()
      CornichonError.catchThrowable(f()) match {
        case Left(e)  => error = Some(BeforeFeatureError(e))
        case Right(_) => successRun = successRun + 1
      }
    }
    error match {
      case Some(e) => Left((e, successRun))
      case None    => Done.rightDone
    }
  }

  private def runAfterFeature(): Either[CornichonError, Done] =
    baseFeature.afterFeature.toList.traverse(f => CornichonError.catchThrowable(f())) match {
      case Left(e)  => Left(AfterFeatureError(e))
      case Right(_) => Done.rightDone
    }
}

object FeatureRunner {
  lazy val availaibleProcessors: Int = Runtime.getRuntime.availableProcessors()
  private val noop = Task.now(Nil)
}
