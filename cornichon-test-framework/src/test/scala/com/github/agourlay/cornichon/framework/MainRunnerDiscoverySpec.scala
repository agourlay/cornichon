package com.github.agourlay.cornichon.framework

import com.github.agourlay.cornichon.framework.discovery.fixtures._
import com.github.agourlay.cornichon.framework.discovery.fixtures.nested.NestedFeature
import munit.FunSuite

class MainRunnerDiscoverySpec extends FunSuite {

  private val fixturesPackage = "com.github.agourlay.cornichon.framework.discovery.fixtures"

  test("discovers concrete features, including inherited and nested ones") {
    val found = MainRunner.discoverFeatureClasses(fixturesPackage).toSet
    val expected: Set[Class[?]] = Set(classOf[DirectFeature], classOf[ViaTraitFeature], classOf[ViaAbstractFeature], classOf[NestedFeature])
    assertEquals(found, expected)
  }

  test("scans only the requested package") {
    val found = MainRunner.discoverFeatureClasses(s"$fixturesPackage.nested")
    assertEquals(found, List(classOf[NestedFeature]))
  }

  test("finds nothing in a package without features") {
    assertEquals(MainRunner.discoverFeatureClasses("com.github.agourlay.cornichon.framework.discovery.missing"), Nil)
  }

  test("replay command uses the MainRunner arguments and points to the feature's package") {
    val replay = MainRunner.mainRunnerReplayCommand(classOf[NestedFeature], "my scenario", 42L)
    assertEquals(
      replay,
      s"""com.github.agourlay.cornichon.framework.MainRunner --packageToScan "$fixturesPackage.nested" --scenarioNameFilter "my scenario" --seed 42"""
    )
    // the printed main class is the real one
    assertEquals(replay.takeWhile(_ != ' '), MainRunner.getClass.getName.stripSuffix("$"))
  }

}
