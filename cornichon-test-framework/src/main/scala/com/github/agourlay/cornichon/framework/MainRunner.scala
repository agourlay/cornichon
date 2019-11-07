package com.github.agourlay.cornichon.framework

import java.util

import cats.syntax.apply._
import com.github.agourlay.cornichon.framework.CornichonFeatureRunner._
import com.github.agourlay.cornichon.CornichonFeature
import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global
import com.monovore.decline._
import com.openpojo.reflection.PojoClass
import com.openpojo.reflection.impl.PojoClassFactory

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MainRunner {

  private val packageToScanOpts = Opts.option[String]("packageToScan", help = "Package containing the feature files.")

  private val featureParallelismOpts = Opts.option[Int]("featureParallelism", help = "Number of feature running in parallel (default=1).")
    .validate("must be positive")(_ > 0).withDefault(1)

  private val seedOpts = Opts.option[Long]("seed", help = "Seed to use for starting random processes.").orNone

  private val scenarioNameFilterOpts = Opts.option[String]("scenarioNameFilter", help = "Filter scenario to run by name.").orNone

  private val mainRunnerCommand = Command(
    name = "cornichon-test-framework",
    header = "Run your cornichon features without SBT."
  )((packageToScanOpts, featureParallelismOpts, seedOpts, scenarioNameFilterOpts).tupled)

  def main(args: Array[String]): Unit = mainRunnerCommand.parse(args, sys.env) match {
    case Left(help) =>
      System.err.println(help)
      sys.exit(1)
    case Right((packageToScan, featureParallelism, explicitSeed, scenarioNameFilter)) =>
      println("Starting discovery")
      val classes = discoverFeatureClasses(packageToScan)
      println(s"Found ${classes.size} features")
      val f = Observable.fromIterable(classes)
        .mapParallelUnordered(featureParallelism) { featureClass =>
          val featureInfo = FeatureInfo(featureClass.getTypeName, featureClass, null, null)
          loadAndExecute(featureInfo, NoOpEventHandler, explicitSeed, scenarioNameFilter.toSet)
        }
        .foldLeftL(true)(_ && _)
        .runToFuture

      if (Await.result(f, Duration.Inf))
        System.exit(0)
      else
        System.exit(1)
  }

  // https://stackoverflow.com/questions/492184/how-do-you-find-all-subclasses-of-a-given-class-in-java
  def discoverFeatureClasses(packageToExplore: String): List[Class[_]] = {
    val classes: util.List[PojoClass] = PojoClassFactory.enumerateClassesByExtendingType(packageToExplore, classOf[CornichonFeature], null)
    classes.asScala.toList.filter(_.isConcrete).map(_.getClazz)
  }
}