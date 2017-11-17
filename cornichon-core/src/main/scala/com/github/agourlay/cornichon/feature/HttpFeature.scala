package com.github.agourlay.cornichon.feature

import cats.syntax.either._
import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.dsl.Dsl.{FromSessionSetter, save_from_session}
import com.github.agourlay.cornichon.http.HttpService.SessionKeys.lastResponseBodyKey
import com.github.agourlay.cornichon.http.{HttpDsl, HttpService, QueryGQL}
import com.github.agourlay.cornichon.json.CornichonJson.jsonStringValue
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.json.{JsonDsl, JsonPath}

import scala.concurrent.duration.FiniteDuration

trait HttpFeature extends HttpDsl with JsonDsl with BaseFeature {

  lazy val requestTimeout = config.requestTimeout
  lazy val baseUrl = config.baseUrl

  def httpServiceByURL(baseUrl: String, timeout: FiniteDuration = requestTimeout) =
    new HttpService(baseUrl, timeout, HttpDsl.globalHttpclient, placeholderResolver, config)

  lazy val http = httpServiceByURL(baseUrl, requestTimeout)

  def query_gql(url: String) = QueryGQL(url, QueryGQL.emptyDocument, None, None, Nil, Nil)

  //FIXME the body is expected to always contains JSON currently
  def body = JsonStepBuilder(placeholderResolver, matcherResolver, SessionKey(lastResponseBodyKey), Some("response body"))

  def save_body(target: String) = save_body_path(JsonPath.root → target)

  def save_body_path(args: (String, String)*) = {
    val inputs = args.map {
      case (path, target) ⇒ FromSessionSetter(lastResponseBodyKey, (session, s) ⇒ {
        for {
          resolvedPath ← placeholderResolver.fillPlaceholders(path)(session)
          jsonPath ← JsonPath.parse(resolvedPath)
          json ← jsonPath.run(s)
        } yield jsonStringValue(json)
      }, target)
    }
    save_from_session(inputs)
  }
}
