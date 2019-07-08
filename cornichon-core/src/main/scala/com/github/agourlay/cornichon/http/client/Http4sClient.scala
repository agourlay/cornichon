package com.github.agourlay.cornichon.http.client

import cats.data.EitherT
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.util.Caching
import io.circe.Json
import monix.eval.Task
import monix.eval.Task._
import monix.execution.Scheduler
import org.http4s._
import org.http4s.circe._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.GZip

import scala.concurrent.duration._
import scala.collection.breakOut
import scala.concurrent.ExecutionContext

class Http4sClient(scheduler: Scheduler, ec: ExecutionContext) extends HttpClient {
  implicit val s = scheduler

  // Lives for the duration of the test run
  private val uriCache = Caching.buildCache[String, Either[CornichonError, Uri]]()
  // Timeouts are managed within the HttpService
  private val defaultHighTimeout = Duration.fromNanos(Long.MaxValue)
  private val (httpClient, safeShutdown) =
    BlazeClientBuilder(executionContext = ec)
      .withDefaultSslContext
      .withMaxTotalConnections(300)
      .withMaxWaitQueueLimit(500)
      .withIdleTimeout(defaultHighTimeout)
      .withResponseHeaderTimeout(defaultHighTimeout)
      .withRequestTimeout(defaultHighTimeout)
      .allocated
      .map { case (client, shutdown) ⇒ GZip()(client) -> shutdown } // always adds `Accept-Encoding` `gzip`
      .runSyncUnsafe(10.seconds)

  private def httpMethodMapper(method: HttpMethod): Method = method match {
    case DELETE  ⇒ org.http4s.Method.DELETE
    case GET     ⇒ org.http4s.Method.GET
    case HEAD    ⇒ org.http4s.Method.HEAD
    case OPTIONS ⇒ org.http4s.Method.OPTIONS
    case PATCH   ⇒ org.http4s.Method.PATCH
    case POST    ⇒ org.http4s.Method.POST
    case PUT     ⇒ org.http4s.Method.PUT
    case other   ⇒ throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  private def buildHeaders(headers: Seq[(String, String)]): Headers = {
    val h: List[Header] = headers.map { case (n, v) ⇒ Header(n, v).parsed }(breakOut)
    Headers(h)
  }

  private def addQueryParams(uri: Uri, moreParams: Seq[(String, String)]): Uri =
    if (moreParams.isEmpty)
      uri
    else {
      val q = Query.fromPairs(moreParams: _*)
      // Not sure it is the most efficient way
      uri.copy(query = Query.fromVector(uri.query.toVector ++ q.toVector))
    }

  private def handleResponse[A](response: Response[Task]): Task[CornichonHttpResponse] = {
    response
      .bodyAsText
      .compile
      .fold("")(_ ++ _)
      .map { decodedBody ⇒
        CornichonHttpResponse(
          status = response.status.code,
          headers = response.headers.toList.map(h ⇒ (h.name.value, h.value))(breakOut),
          body = decodedBody
        )
      }
  }

  override def runRequest(cReq: HttpRequest[Json], t: FiniteDuration): EitherT[Task, CornichonError, CornichonHttpResponse] =
    parseUri(cReq.url).fold(
      e ⇒ EitherT.left[CornichonHttpResponse](Task.now(e)),
      uri ⇒ EitherT {
        val req = Request[Task](httpMethodMapper(cReq.method))
          .withHeaders(buildHeaders(cReq.headers))
          .withUri(addQueryParams(uri, cReq.params))

        val completeRequest = cReq.body.fold(req)(b ⇒ req.withEntity(b))
        val response = httpClient.fetch(completeRequest)(handleResponse).map(_.asRight[CornichonError])
        val timeout = Task.delay(TimeoutErrorAfter(cReq, t).asLeft).delayExecution(t)

        Task.race(response, timeout)
          .map(_.fold(identity, identity))
          .onErrorRecover { case t: Throwable ⇒ RequestError(cReq, t).asLeft }
      }
    )

  // TODO SSE support https://github.com/http4s/http4s/issues/619
  def openStream(req: HttpStreamedRequest, t: FiniteDuration): Task[Either[CornichonError, CornichonHttpResponse]] = ???

  def shutdown(): Task[Done] =
    safeShutdown.map { _ ⇒ uriCache.invalidateAll(); Done }

  def paramsFromUrl(url: String): Either[CornichonError, List[(String, String)]] =
    if (url.contains('?'))
      parseUri(url).map(_.params.toList)
    else
      rightNil

  private def parseUri(uri: String): Either[CornichonError, Uri] =
    uriCache.get(uri, u ⇒ Uri.fromString(u).leftMap(e ⇒ MalformedUriError(u, e.message)))

}
