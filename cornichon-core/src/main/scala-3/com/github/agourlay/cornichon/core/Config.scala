package com.github.agourlay.cornichon.core

import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.*
import pureconfig.generic.semiauto.*

import scala.concurrent.duration.*

final case class Config(
    executeScenariosInParallel: Boolean = true,
    scenarioExecutionParallelismFactor: Int = 1,
    requestTimeout: FiniteDuration = 2000.millis,
    globalBaseUrl: String = "",
    traceRequests: Boolean = false,
    warnOnDuplicateHeaders: Boolean = false,
    failOnDuplicateHeaders: Boolean = false,
    addAcceptGzipByDefault: Boolean = false, // kinda slow
    disableCertificateVerification: Boolean = false,
    followRedirect: Boolean = false,
    enableHttp2: Boolean = false
)

object Config {

  // because default HOCON reader config is kebab case
  given [A]: ProductHint[A]  = ProductHint(ConfigFieldMapping(CamelCase, CamelCase), allowUnknownKeys = false)
  given ConfigReader[Config] = deriveReader[Config]

  def load(at: String) = ConfigSource.default.at(at).load[Config]

}
