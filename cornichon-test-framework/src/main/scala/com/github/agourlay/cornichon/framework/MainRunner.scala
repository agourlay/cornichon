package com.github.agourlay.cornichon.framework

import java.util

import cats.syntax.apply._
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.framework.CornichonFeatureRunner._
import com.monovore.decline._
import com.openpojo.reflection.PojoClass
import com.openpojo.reflection.impl.PojoClassFactory
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sbt.testing.TestSelector

import scala.jdk.CollectionConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MainRunner {

  private val packageToScanOpts = Opts.option[String]("packageToScan", help = "Package containing the feature files.")

  private val reportsOutputDirOpts = Opts.option[String]("reportsOutputDir", help = "Output directory for junit.xml files (default to current).").withDefault(".")

  private val featureParallelismOpts = Opts.option[Int]("featureParallelism", help = "Number of feature running in parallel (default=1).")
    .validate("must be positive")(_ > 0).withDefault(1)

  private val seedOpts = Opts.option[Long]("seed", help = "Seed to use for starting random processes.").orNone

  private val scenarioNameFilterOpts = Opts.option[String]("scenarioNameFilter", help = "Filter scenario to run by name.").orNone

  private val mainRunnerCommand = Command(
    name = "cornichon-test-framework",
    header = "Run your cornichon features without SBT."
  )((packageToScanOpts, reportsOutputDirOpts, featureParallelismOpts, seedOpts, scenarioNameFilterOpts).tupled)

  def main(args: Array[String]): Unit = mainRunnerCommand.parse(args.toSeq, sys.env) match {
    case Left(help) =>
      System.err.println(help)
      sys.exit(1)
    case Right((packageToScan, reportsOutputDir, featureParallelism, explicitSeed, scenarioNameFilter)) =>
      JUnitXmlReporter.checkReportsFolder(reportsOutputDir)
      println("Starting feature classes discovery")
      val classes = discoverFeatureClasses(packageToScan)
      println(s"Found ${classes.size} feature classes")
      val scenarioNameFilterSet = scenarioNameFilter.toSet
      val f = Observable.fromIterable(classes)
        .mapParallelUnordered(featureParallelism) { featureClass =>
          val startedAt = System.currentTimeMillis()
          val featureTypeName = featureClass.getTypeName
          val featureInfo = FeatureInfo(featureTypeName, featureClass, CornichonFingerprint, new TestSelector(featureTypeName))
          val eventHandler = new RecordEventHandler()
          loadAndExecute(featureInfo, eventHandler, explicitSeed, scenarioNameFilterSet)
            .timed
            .map {
              case (duration, res) =>
                JUnitXmlReporter.writeJunitReport(reportsOutputDir, featureTypeName, duration, startedAt, eventHandler.recorded) match {
                  case Left(e) =>
                    println(s"ERROR: Could not generate JUnit xml report for $featureTypeName due to\n${CornichonError.genStacktrace(e)}")
                  case Right(_) =>
                    ()
                }
                res
            }
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
    classes.iterator().asScala.collect { case pojo if pojo.isConcrete => pojo.getClazz }.toList
  }
}