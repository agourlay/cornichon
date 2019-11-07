package com.github.agourlay.cornichon.framework

import com.github.agourlay.cornichon.framework.CornichonFeatureRunner._
import monix.execution.Scheduler.Implicits.global
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
    Await.result(featureTask.runToFuture, Duration.Inf)
    Array.empty
  }
}