package com.github.agourlay.cornichon.sbtinterface

import sbt.testing.SubclassFingerprint

object CornichonFingerprint extends SubclassFingerprint {
  override def isModule: Boolean = false

  override def superclassName(): String = "com.github.agourlay.cornichon.CornichonFeature"

  def requireNoArgConstructor(): Boolean = false
}