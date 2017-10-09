package com.github.agourlay.cornichon.core

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
