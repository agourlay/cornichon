package com.github.agourlay.cornichon.http.client

import cats.data.EitherT
import cats.syntax.either._
import cats.instances.future._
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http._
import fs2.Task
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.client.blaze.{ BlazeClientConfig, PooledHttp1Client }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }

// Experimental client
// TODO Gzip support
// TODO request timeout
// TODO SSE
// TODO offer toggle in baseFeature to pick it
class Http4sClient(implicit executionContext: ExecutionContext) extends HttpClient {

  private val httpClient = PooledHttp1Client(
    maxTotalConnections = 100,
    config = BlazeClientConfig.defaultConfig.copy(idleTimeout = Duration.Inf)
  )

  def httpMethodMapper(method: HttpMethod): Method = method match {
    case DELETE  ⇒ org.http4s.Method.DELETE
    case GET     ⇒ org.http4s.Method.GET
    case HEAD    ⇒ org.http4s.Method.HEAD
    case OPTIONS ⇒ org.http4s.Method.OPTIONS
    case PATCH   ⇒ org.http4s.Method.PATCH
    case POST    ⇒ org.http4s.Method.POST
    case PUT     ⇒ org.http4s.Method.PUT
    case other   ⇒ throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  def buildHeaders(headers: Seq[(String, String)]): Headers =
    Headers(headers.toList.map { case (n, v) ⇒ Header(n, v).parsed })

  def addQueryParams(uri: Uri, moreParams: Seq[(String, String)]): Uri = {
    val q = Query.fromPairs(moreParams: _*)
    //Not sure it is not most efficient way
    uri.copy(query = Query(uri.query.toVector ++ q.toVector: _*))
  }

  def handleResponse[A](response: Response): Task[CornichonHttpResponse] = {
    response
      .bodyAsText
      .runFold("")(_ ++ _)
      .map { decodedBody ⇒
        CornichonHttpResponse(
          status = response.status.code,
          headers = response.headers.toList.map(h ⇒ (h.name.value, h.value)),
          body = decodedBody
        )
      }
  }

  override def runRequest(cReq: HttpRequest[Json], t: FiniteDuration): EitherT[Future, CornichonError, CornichonHttpResponse] =
    Uri.fromString(cReq.url).fold(
      e ⇒ EitherT.left[Future, CornichonError, CornichonHttpResponse](Future.successful(MalformedHeadersError(e.message))),
      uri ⇒ EitherT[Future, CornichonError, CornichonHttpResponse] {
        val r = Request(httpMethodMapper(cReq.method))
          .withHeaders(buildHeaders(cReq.headers))
          .withUri(addQueryParams(uri, cReq.params))

        cReq.body
          .fold(Task.now(r))(b ⇒ r.withBody(b))
          .flatMap(r ⇒ httpClient.fetch(r)(handleResponse))
          .unsafeRunAsyncFuture()
          .map(Right(_))
      }
    )

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) = ???

  def shutdown() = httpClient.shutdown.unsafeRunAsyncFuture().map(_ ⇒ Done)

  def paramsFromUrl(url: String) = Uri.unsafeFromString(url).params.toList
}
