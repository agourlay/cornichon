package com.github.agourlay.cornichon.framework

import java.util.concurrent.atomic.AtomicBoolean

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.framework.CornichonSbtRunner._
import com.github.agourlay.cornichon.dsl.BaseFeature
import sbt.testing._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

class CornichonSbtRunner(val args: Array[String], val remoteArgs: Array[String]) extends Runner {

  private val gotTasks = new AtomicBoolean(false)

  // A task is a Feature class
  override def tasks(taskDefs: Array[TaskDef]): Array[Task] = {
    gotTasks.set(true)
    val (seeds, scenarioNameFilter) = args.toSet.partition(_.startsWith(seedArg))
    val explicitSeed = try {
      seeds.headOption.map(_.split(seedArg)(1).toLong) //YOLO
    } catch {
      case e if NonFatal(e) =>
        println(s"Could not parse seed from args ${args.toList}")
        println(CornichonError.genStacktrace(e))
        None
    }
    taskDefs.map(new CornichonFeatureSbtTask(_, scenarioNameFilter, explicitSeed))
  }

  override def done(): String = {
    // Function called twice - main and forked process
    // https://github.com/sbt/sbt/issues/3510
    if (gotTasks.get) Await.result(BaseFeature.shutDownGlobalResources(), Duration.Inf)
    // empty strings means letting SBT generate the result summary
    ""
  }

}

object CornichonSbtRunner {
  val seedArg = "--seed="
}