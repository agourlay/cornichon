package com.github.agourlay.cornichon.core

import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.generic.ProductHint
import pureconfig.{ CamelCase, ConfigFieldMapping }
import pureconfig.generic.auto._

import scala.concurrent.duration._

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
  implicit val hint: ProductHint[Config] = ProductHint[Config](allowUnknownKeys = false, fieldMapping = ConfigFieldMapping(CamelCase, CamelCase))

  def load(at: String): Result[Config] = ConfigSource.default.at(at).load[Config]
}
