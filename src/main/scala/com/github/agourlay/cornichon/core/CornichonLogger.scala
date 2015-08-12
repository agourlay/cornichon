package com.github.agourlay.cornichon.core

import org.slf4j.LoggerFactory

trait CornichonLogger {
  val log = LoggerFactory.getLogger("Cornichon")
}
