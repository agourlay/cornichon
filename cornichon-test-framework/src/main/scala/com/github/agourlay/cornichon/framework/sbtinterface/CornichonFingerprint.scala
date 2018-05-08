package com.github.agourlay.cornichon.framework.sbtinterface

import sbt.testing.SubclassFingerprint

object CornichonFingerprint extends SubclassFingerprint {
  override def isModule: Boolean = false

  override def superclassName(): String = "com.github.agourlay.cornichon.framework.CornichonFeature"

  def requireNoArgConstructor(): Boolean = false
}