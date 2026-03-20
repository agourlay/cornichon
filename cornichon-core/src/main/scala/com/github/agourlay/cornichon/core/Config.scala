package com.github.agourlay.cornichon.core

import com.typesafe.config.{ConfigException, ConfigFactory}

import scala.concurrent.duration._
import scala.util.Try

final case class Config(
  executeScenariosInParallel: Boolean = true,
  scenarioExecutionParallelismFactor: Int = 1,
  requestTimeout: FiniteDuration = 2000.millis,
  globalBaseUrl: String = "",
  traceRequests: Boolean = false,
  warnOnDuplicateHeaders: Boolean = false,
  failOnDuplicateHeaders: Boolean = false,
  addAcceptGzipByDefault: Boolean = false, // disabled by default due to decompression overhead
  disableCertificateVerification: Boolean = false,
  followRedirect: Boolean = false,
  enableHttp2: Boolean = false
)

object Config {

  private val knownKeys = Set(
    "executeScenariosInParallel",
    "scenarioExecutionParallelismFactor",
    "requestTimeout",
    "globalBaseUrl",
    "traceRequests",
    "warnOnDuplicateHeaders",
    "failOnDuplicateHeaders",
    "addAcceptGzipByDefault",
    "disableCertificateVerification",
    "followRedirect",
    "enableHttp2"
  )

  def load(at: String): Either[Throwable, Config] =
    Try {
      val root = ConfigFactory.load()
      if (!root.hasPath(at))
        return Right(Config())
      val c = root.getConfig(at)

      // Reject unknown keys to catch typos early
      val actualKeys = {
        import scala.jdk.CollectionConverters._
        c.root().keySet().asScala.toSet
      }
      val unknownKeys = actualKeys -- knownKeys
      if (unknownKeys.nonEmpty)
        throw new ConfigException.Generic(s"Unknown cornichon configuration keys: ${unknownKeys.mkString(", ")}")

      val defaults = Config()
      Config(
        executeScenariosInParallel = if (c.hasPath("executeScenariosInParallel")) c.getBoolean("executeScenariosInParallel") else defaults.executeScenariosInParallel,
        scenarioExecutionParallelismFactor =
          if (c.hasPath("scenarioExecutionParallelismFactor")) c.getInt("scenarioExecutionParallelismFactor") else defaults.scenarioExecutionParallelismFactor,
        requestTimeout = if (c.hasPath("requestTimeout")) c.getDuration("requestTimeout").toMillis.millis else defaults.requestTimeout,
        globalBaseUrl = if (c.hasPath("globalBaseUrl")) c.getString("globalBaseUrl") else defaults.globalBaseUrl,
        traceRequests = if (c.hasPath("traceRequests")) c.getBoolean("traceRequests") else defaults.traceRequests,
        warnOnDuplicateHeaders = if (c.hasPath("warnOnDuplicateHeaders")) c.getBoolean("warnOnDuplicateHeaders") else defaults.warnOnDuplicateHeaders,
        failOnDuplicateHeaders = if (c.hasPath("failOnDuplicateHeaders")) c.getBoolean("failOnDuplicateHeaders") else defaults.failOnDuplicateHeaders,
        addAcceptGzipByDefault = if (c.hasPath("addAcceptGzipByDefault")) c.getBoolean("addAcceptGzipByDefault") else defaults.addAcceptGzipByDefault,
        disableCertificateVerification =
          if (c.hasPath("disableCertificateVerification")) c.getBoolean("disableCertificateVerification") else defaults.disableCertificateVerification,
        followRedirect = if (c.hasPath("followRedirect")) c.getBoolean("followRedirect") else defaults.followRedirect,
        enableHttp2 = if (c.hasPath("enableHttp2")) c.getBoolean("enableHttp2") else defaults.enableHttp2
      )
    }.toEither

}
