package com.github.agourlay.cornichon.experimental.sbtinterface

import java.util.concurrent.atomic.AtomicBoolean

import com.github.agourlay.cornichon.feature.BaseFeature
import sbt.testing._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class CornichonRunner(val args: Array[String], val remoteArgs: Array[String]) extends Runner {

  private val gotTasks = new AtomicBoolean(false)

  override def tasks(taskDefs: Array[TaskDef]) = {
    BaseFeature.disableAutomaticResourceCleanup()
    gotTasks.set(true)
    taskDefs.map(new SbtCornichonTask(_))
  }

  override def done(): String = {
    // Function called twice - main and forked process
    // https://github.com/sbt/sbt/issues/3510
    if (gotTasks.get) Await.result(BaseFeature.shutDownGlobalResources(), Duration.Inf)
    // empty strings means letting SBT generate the result summary
    ""
  }

}