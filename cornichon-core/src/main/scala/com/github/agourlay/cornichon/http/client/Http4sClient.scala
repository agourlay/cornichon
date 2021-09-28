package com.github.agourlay.cornichon.http.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import cats.Show
import cats.data.EitherT
import cats.syntax.either._
import cats.syntax.show._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpStreams.SSE
import com.github.agourlay.cornichon.util.Caching
import com.github.agourlay.cornichon.util.CirceUtil._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import javax.net.ssl.{ SSLContext, TrustManager, X509TrustManager }
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.middleware.{ FollowRedirect, GZip }
import org.typelevel.ci.CIString

import scala.concurrent.duration._

class Http4sClient(
    addAcceptGzipByDefault: Boolean,
    disableCertificateVerification: Boolean,
    followRedirect: Boolean)(implicit ioRuntime: IORuntime)
  extends HttpClient {
  // Disable JDK built-in checks
  private val sslContext = {
    if (disableCertificateVerification) {
      val ssl = SSLContext.getInstance("SSL")
      val byPassTrustManagers = Array[TrustManager](new X509TrustManager() {
        override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
        override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = ()
        override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = ()
      })
      ssl.init(null, byPassTrustManagers, new SecureRandom)
      ssl
    } else SSLContext.getDefault
  }

  // Lives for the duration of the test run
  private val uriCache = Caching.buildCache[String, Either[CornichonError, Uri]]()
  // Timeouts are managed within the HttpService
  private val defaultHighTimeout = Duration.Inf
  private val (httpClient, safeShutdown) =
    BlazeClientBuilder[IO](executionContext = scala.concurrent.ExecutionContext.global)
      .withSslContext(sslContext)
      .withMaxTotalConnections(300)
      .withMaxWaitQueueLimit(500)
      .withIdleTimeout(defaultHighTimeout)
      .withResponseHeaderTimeout(defaultHighTimeout)
      .withRequestTimeout(defaultHighTimeout)
      .allocated
      .map {
        case (client, shutdown) =>
          val c1 = if (addAcceptGzipByDefault) GZip()(client) else client
          val c2 = if (followRedirect) FollowRedirect(maxRedirects = 10)(client = c1) else c1
          c2 -> shutdown
      }.unsafeRunSync()

  private def toHttp4sMethod(method: HttpMethod): Method = method match {
    case DELETE  => org.http4s.Method.DELETE
    case GET     => org.http4s.Method.GET
    case HEAD    => org.http4s.Method.HEAD
    case OPTIONS => org.http4s.Method.OPTIONS
    case PATCH   => org.http4s.Method.PATCH
    case POST    => org.http4s.Method.POST
    case PUT     => org.http4s.Method.PUT
    case other   => throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  private def toHttp4sHeaders(headers: Seq[(String, String)]): List[Header.Raw] =
    headers.iterator.map { case (n, v) => Header.Raw(CIString(n), v) }.toList

  private def fromHttp4sHeaders(headers: Headers): Seq[(String, String)] =
    headers.headers.map(h => (h.name.toString, h.value))

  def addQueryParams(uri: Uri, moreParams: Seq[(String, String)]): Uri =
    if (moreParams.isEmpty)
      uri
    else {
      val q = Query.fromPairs(moreParams: _*)
      // Not sure it is the most efficient way
      uri.copy(query = Query.fromVector(uri.query.toVector ++ q.toVector))
    }

  override def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(implicit ee: EntityEncoder[IO, A]): EitherT[IO, CornichonError, HttpResponse] =
    parseUri(cReq.url).fold(
      e => EitherT.left[HttpResponse](IO.pure(e)),
      uri => EitherT {
        val req = Request[IO](toHttp4sMethod(cReq.method))
        val completeRequest = cReq.body.fold(req)(b => req.withEntity(b))
          .putHeaders(toHttp4sHeaders(cReq.headers)) // `withEntity` adds `Content-Type` so we set the headers afterwards to have the possibility to override it
          .withUri(addQueryParams(uri, cReq.params))
        val cornichonResponse = httpClient.run(completeRequest).use { http4sResp =>
          http4sResp
            .bodyText
            .compile
            .string
            .map { decodedBody =>
              HttpResponse(
                status = http4sResp.status.code,
                headers = fromHttp4sHeaders(http4sResp.headers),
                body = decodedBody
              ).asRight[CornichonError]
            }
        }

        val timeout = IO.delay(TimeoutErrorAfter(cReq, t).asLeft).delayBy(t)

        IO.race(cornichonResponse, timeout)
          .map(_.fold(identity, identity))
          .handleError { t => RequestError(cReq, t).asLeft }
      }
    )

  private val sseHeader = "text" -> "event-stream"

  private def runSSE(streamReq: HttpStreamedRequest, t: FiniteDuration): EitherT[IO, CornichonError, HttpResponse] = {
    parseUri(streamReq.url).fold(
      e => EitherT.left[HttpResponse](IO.pure(e)),
      uri => EitherT {
        val req = Request[IO](org.http4s.Method.GET)
          .withHeaders(Headers(toHttp4sHeaders(streamReq.addHeaders(sseHeader).headers)))
          .withUri(addQueryParams(uri, streamReq.params))

        val cornichonResponse = httpClient.run(req).use { http4sResp =>
          http4sResp
            .body
            .through(ServerSentEvent.decoder)
            .interruptAfter(streamReq.takeWithin)
            .filter(_ != ServerSentEvent.empty) // filter out empty SSE
            .compile
            .toList
            .map { events =>
              HttpResponse(
                status = http4sResp.status.code,
                headers = fromHttp4sHeaders(http4sResp.headers),
                body = Json.fromValues(events.iterator.map(_.asJson).toVector).show
              ).asRight[CornichonError]
            }
        }

        val timeout = IO.delay(TimeoutErrorAfter(streamReq, t).asLeft).delayBy(t)

        IO.race(cornichonResponse, timeout)
          .map(_.fold(identity, identity))
          .handleError { t => RequestError(streamReq, t).asLeft }
      }
    )
  }

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): IO[Either[CornichonError, HttpResponse]] =
    req.stream match {
      case SSE => runSSE(req, t).value
      case _   => ??? // TODO implement WS support
    }

  def shutdown(): IO[Done] =
    safeShutdown.map { _ => uriCache.invalidateAll(); Done }

  def paramsFromUrl(url: String): Either[CornichonError, List[(String, String)]] =
    if (url.contains('?'))
      parseUri(url).map(_.params.toList)
    else
      rightNil

  def parseUri(uri: String): Either[CornichonError, Uri] =
    uriCache.get(uri, u => Uri.fromString(u).leftMap(e => MalformedUriError(u, e.message)))
}