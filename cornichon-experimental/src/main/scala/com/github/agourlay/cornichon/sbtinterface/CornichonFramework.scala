package com.github.agourlay.cornichon.sbtinterface

import sbt.testing._

private[cornichon] class CornichonFramework extends Framework {

  override def fingerprints(): Array[Fingerprint] = Array(new CornichonFingerprint)

  def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
    new CornichonRunner(args, remoteArgs, testClassLoader)

  override def name(): String = "cornichon"

}