package com.github.agourlay.cornichon.framework

import cats.syntax.apply._
import cats.effect.IO
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.framework.CornichonFeatureRunner._
import com.monovore.decline._
import io.github.classgraph.ClassGraph
import fs2.Stream
import sbt.testing.TestSelector

import scala.jdk.CollectionConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MainRunner {

  private val packageToScanOpts = Opts.option[String]("packageToScan", help = "Package containing the feature files.")

  private val reportsOutputDirOpts = Opts.option[String]("reportsOutputDir", help = "Output directory for junit.xml files (default to current).").withDefault(".")

  private val featureParallelismOpts = Opts
    .option[Int]("featureParallelism", help = "Number of feature running in parallel (default=1).")
    .validate("must be positive")(_ > 0)
    .withDefault(1)

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
      if (classes.isEmpty) {
        // Classpath scanning cannot tell a mistyped package from an empty one, and an empty run
        // otherwise reports success - so a typo in `--packageToScan` would exit 0 having run nothing.
        System.err.println(s"ERROR: no feature class found in package '$packageToScan'")
        sys.exit(1)
      }
      val scenarioNameFilterSet = scenarioNameFilter.toSet
      val f = Stream
        .iterable[IO, Class[?]](classes)
        .mapAsyncUnordered(featureParallelism) { featureClass =>
          val startedAt = System.currentTimeMillis()
          val featureTypeName = featureClass.getTypeName
          val featureInfo = FeatureInfo(featureTypeName, featureClass, CornichonFingerprint, new TestSelector(featureTypeName))
          val eventHandler = new RecordEventHandler()
          loadAndExecute(featureInfo, eventHandler, explicitSeed, scenarioNameFilterSet).timed
            .map { case (duration, res) =>
              JUnitXmlReporter.writeJunitReport(reportsOutputDir, featureTypeName, duration, startedAt, eventHandler.recorded) match {
                case Left(e) =>
                  println(s"ERROR: Could not generate JUnit xml report for $featureTypeName due to\n${CornichonError.genStacktrace(e)}")
                case Right(_) =>
                  ()
              }
              res
            }
        }
        .compile
        .fold(true)(_ && _)
        .unsafeToFuture()(using cats.effect.unsafe.implicits.global)

      if (Await.result(f, Duration.Inf))
        System.exit(0)
      else
        System.exit(1)
  }

  // `CornichonFeature` is a trait, so on the JVM it is an interface - `getSubclasses` finds nothing here.
  // `getStandardClasses` drops interfaces and annotations, leaving the concrete features once abstract ones are filtered out.
  private def discoverFeatureClasses(packageToExplore: String): List[Class[?]] = {
    val scanResult = new ClassGraph().enableClassInfo().acceptPackages(packageToExplore).scan()
    try
      scanResult
        .getClassesImplementing(classOf[CornichonFeature].getName)
        .getStandardClasses
        .asScala
        .iterator
        .filterNot(_.isAbstract)
        .map(_.loadClass())
        .toList
    finally scanResult.close()
  }

}
