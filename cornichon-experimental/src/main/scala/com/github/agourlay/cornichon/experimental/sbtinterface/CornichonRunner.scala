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
    implicit val ec = BaseFeature.globalRuntime._4
    taskDefs.map(new SbtCornichonTask(_))
  }

  override def done(): String = {
    //For some reason CornichonRunner is instantiated twice thus this is called twice?!
    if (gotTasks.get) Await.result(BaseFeature.shutDownGlobalResources(), Duration.Inf)
    ""
  }

}