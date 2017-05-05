package com.github.agourlay.cornichon.sbtinterface

import java.util.concurrent.atomic.AtomicBoolean

import com.github.agourlay.cornichon.feature.BaseFeature
import sbt.testing._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

private[cornichon] class CornichonRunner(
    val args: Array[String],
    val remoteArgs: Array[String],
    testClassLoader: ClassLoader
) extends Runner {

  val gotTasks = new AtomicBoolean(false)

  override def tasks(taskDefs: Array[TaskDef]) = {
    gotTasks.set(true)
    taskDefs.map(new SbtCornichonTask(_))
  }

  //For some reason CornichonRunner is instantiated twice thus this is called twice?!
  //TODO build nice summary report instead of 'Done!"
  override def done(): String = {
    if (gotTasks.get)
      Await.result(BaseFeature.shutDownGlobalResources(), Duration.Inf)
    "Done!"
  }

}