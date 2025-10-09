package com.github.agourlay.cornichon.framework

import com.github.agourlay.cornichon.framework.CornichonFeatureRunner._
import sbt.testing._

import scala.concurrent.duration.Duration
import scala.concurrent.Await

class CornichonFeatureSbtTask(task: TaskDef, scenarioNameFilter: Set[String], explicitSeed: Option[Long]) extends Task {

  override def tags(): Array[String] = Array.empty
  override def taskDef(): TaskDef = task

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val fqn = task.fullyQualifiedName()
    val featureInfo = FeatureInfo(fqn, Class.forName(fqn), task.fingerprint(), task.selectors().head)
    val featureTask = loadAndExecute(featureInfo, eventHandler, explicitSeed, scenarioNameFilter)
    val _ = Await.result(featureTask.unsafeToFuture()(cats.effect.unsafe.implicits.global), Duration.Inf)
    Array.empty
  }
}