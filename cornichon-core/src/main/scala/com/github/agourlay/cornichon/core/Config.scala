package com.github.agourlay.cornichon.core

import scala.concurrent.duration._

case class Config(
    executeScenariosInParallel: Boolean = true,
    requestTimeout: FiniteDuration = 2000.millis,
    baseUrl: String = "",
    traceRequest: Boolean = false,
    useExperimentalHttp4sClient: Boolean = false
)
