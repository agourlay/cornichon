package com.github.agourlay.cornichon.sbtinterface

import sbt.testing._

private[cornichon] class CornichonRunner(
    val args: Array[String],
    val remoteArgs: Array[String],
    testClassLoader: ClassLoader
) extends Runner {

  override def tasks(taskDefs: Array[TaskDef]) = {
    //TODO call resource setup
    taskDefs.map(new SbtCornichonTask(_, testClassLoader))
  }

  override def done(): String = {
    //TODO call resource teardown
    "Done!"
  }

}