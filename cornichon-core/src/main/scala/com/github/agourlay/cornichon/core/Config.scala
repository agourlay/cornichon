package com.github.agourlay.cornichon.core

import pureconfig.{ CamelCase, ConfigFieldMapping, ProductHint }

import scala.concurrent.duration._

case class Config(
    executeScenariosInParallel: Boolean = true,
    requestTimeout: FiniteDuration = 2000.millis,
    baseUrl: String = "",
    traceRequests: Boolean = false,
    useExperimentalHttp4sClient: Boolean = false,
    warnOnDuplicateHeaders: Boolean = false,
    failOnDuplicateHeaders: Boolean = false,
    addAcceptGzipByDefault: Boolean = false
)

object Config {
  implicit val hint = ProductHint[Config](allowUnknownKeys = false, fieldMapping = ConfigFieldMapping(CamelCase, CamelCase))
}