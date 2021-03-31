package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import cats.effect._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpStreams.SSE
import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.util.Caching
import io.circe.syntax.EncoderOps
import io.circe.{ Encoder, Json }
import io.netty.handler.ssl.{ ApplicationProtocolConfig, ClientAuth, IdentityCipherSuiteFilter, JdkSslContext }
import monix.eval.Task
import monix.eval.Task._
import monix.execution.Scheduler
import monix.reactive.Observable
import org.asynchttpclient.{ AsyncHttpClient, DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig }
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{ FollowRedirect, GZip }
import sttp.capabilities.Effect
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.monix.MonixStreams
import sttp.client3
import sttp.client3._
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend
import sttp.client3.http4s._
import sttp.client3.impl.fs2.Fs2ServerSentEvents
import sttp.client3.impl.monix.MonixServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.model.{ Uri => SttpUri }

import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.{ SSLContext, TrustManager, X509TrustManager }
import scala.concurrent._
import scala.concurrent.duration._

/**
 * @param sttp          the sttp backend
 * @param streamHandler handler to convert sse events in to assertable JSON values
 * @param shutdown      release effect to close the backend
 * @tparam P the capabilities of the backend
 * @tparam R the requirements (on the capabilities) for the request
 *
 *           NOTE: the type param bounds reflect the the requirements for {{SttpBackend.send}}
 */
final case class HttpClientBackend[P, R >: P with Effect[Task]](
    sttp: SttpBackend[Task, P],
    streamHandler: StreamHandler[R],
    shutdown: Task[Unit])

trait StreamHandler[R] {
  def apply(takeWithin: FiniteDuration): RequestT[Identity, Either[String, String], R] => RequestT[Identity, Either[String, Json], R]
}

class Http4sClient[P, R >: P with Effect[Task]](backend: HttpClientBackend[P, R])
  extends HttpClient {
  // Lives for the duration of the test run
  private val uriCache = Caching.buildCache[String, Either[CornichonError, SttpUri]]()

  private def toSttpRequest[A](cReq: HttpRequest[A], uri: SttpUri)(implicit ee: BodySerializer[A]): client3.Request[Either[String, String], R] = {
    val uriWithParams = uri.addParams(cReq.params: _*)

    val request = (cReq.method match {
      case DELETE  => basicRequest.delete(uriWithParams)
      case GET     => basicRequest.get(uriWithParams)
      case HEAD    => basicRequest.head(uriWithParams)
      case OPTIONS => basicRequest.options(uriWithParams)
      case PATCH   => basicRequest.patch(uriWithParams)
      case POST    => basicRequest.post(uriWithParams)
      case PUT     => basicRequest.put(uriWithParams)
      case other   => throw CornichonException(s"unsupported HTTP method ${other.name}")
    })

    cReq
      .body.fold(request)(body => request.body(body))
      .headers(cReq.headers.toMap)
  }

  override def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(implicit ee: BodySerializer[A]): EitherT[Task, CornichonError, HttpResponse] =
    parseUri(cReq.url).fold(
      e => EitherT.left[HttpResponse](Task.now(e)),
      uri => EitherT {
        val cornichonResponse: Task[Either[CornichonError, HttpResponse]] = toSttpRequest(cReq, uri).send[Task, P](backend.sttp).map { response =>
          val bodyValue = response.body.fold(identity, identity)
          HttpResponse(
            status = response.code.code,
            headers = response.headers.map(header => (header.name, header.value)),
            body = bodyValue
          ).asRight[CornichonError]
        }

        val timeout = Task.delay(TimeoutErrorAfter(cReq, t).asLeft).delayExecution(t)

        Task.race(cornichonResponse, timeout)
          .map(_.fold(identity, identity))
          .onErrorRecover { case t: Throwable => RequestError(cReq, t).asLeft }
      }
    )

  private val sseHeader = "text" -> "event-stream"

  private def runSSE(streamReq: HttpStreamedRequest, t: FiniteDuration): EitherT[Task, CornichonError, HttpResponse] = {
    parseUri(streamReq.url).fold(
      e => EitherT.left[HttpResponse](Task.now(e)),
      uri => EitherT {
        val sseRequest = backend.streamHandler(streamReq.takeWithin)(basicRequest
          .get(uri.addParams(streamReq.params.toMap))
          .headers(streamReq.addHeaders(sseHeader).headers.toMap))

        val cornichonResponse: Task[Either[CornichonError, HttpResponse]] = sseRequest.send(backend.sttp).map { streamResponse =>
          val bodyValue: String = streamResponse.body.fold(identity, _.noSpaces)

          HttpResponse(
            status = streamResponse.code.code,
            headers = streamResponse.headers.map(header => (header.name, header.value)),
            body = bodyValue
          ).asRight[CornichonError]
        }

        val timeout = Task.delay(TimeoutErrorAfter(streamReq, t).asLeft).delayExecution(t)

        Task.race(cornichonResponse, timeout)
          .map(_.fold(identity, identity))
          .onErrorRecover { case t: Throwable => RequestError(streamReq, t).asLeft }
      }
    )
  }

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): Task[Either[CornichonError, HttpResponse]] =
    req.stream match {
      case SSE => runSSE(req, t).value
      case _   => ??? // TODO implement WS support
    }

  def shutdown(): Task[Done] =
    backend.shutdown.map { _ => uriCache.invalidateAll(); Done }

  def paramsFromUrl(url: String): Either[CornichonError, List[(String, String)]] =
    if (url.contains('?'))
      parseUri(url).map(_.paramsSeq.toList)
    else
      rightNil

  def parseUri(uri: String): Either[CornichonError, SttpUri] =
    uriCache.get(uri, u => SttpUri.parse(u).leftMap(error => MalformedUriError(u, error)))
}

object SttpClient {

  sealed trait BackendOption {
    val key: String
  }

  case object Https4BackendOption extends BackendOption {
    val key: String = "https"
  }

  case object AsyncHttpOption extends BackendOption {
    val key: String = "async"
  }

  object BackendOption {
    def apply(key: String): BackendOption = key match {
      case Https4BackendOption.key => Https4BackendOption
      case AsyncHttpOption.key     => AsyncHttpOption
    }
  }

  // an implicit `cats.effect.ContextShift` is required to create an instance of `cats.effect.Concurrent`
  // for `cats.effect.IO`,  as well as a `cats.effect.Blocker` instance.
  // Note that you'll probably want to use a different thread pool for blocking.
  private val blocker: cats.effect.Blocker = Blocker.liftExecutionContext(ExecutionContext.global)

  // Timeouts are managed within the HttpService
  private val defaultHighTimeout = Duration(60, TimeUnit.MINUTES)

  private val maxConnections: Int = 300

  private implicit val sseEncoder: Encoder.AsObject[ServerSentEvent] = io.circe.generic.semiauto.deriveEncoder[ServerSentEvent]

  // Disable JDK built-in checks
  private def createSslContext(disableCertificateVerification: Boolean): SSLContext = {
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

  def blazeClientBuilder(disableCertificateVerification: Boolean)(implicit scheduler: Scheduler): BlazeClientBuilder[Task] = {
    BlazeClientBuilder(executionContext = scheduler)
      .withSslContext(createSslContext(disableCertificateVerification))
      .withMaxTotalConnections(maxConnections)
      .withMaxWaitQueueLimit(500)
      .withIdleTimeout(defaultHighTimeout)
      .withResponseHeaderTimeout(defaultHighTimeout)
      .withRequestTimeout(defaultHighTimeout)
  }

  private val fs2StreamHandler: StreamHandler[Effect[Task] with Fs2Streams[Task]] = new StreamHandler[Effect[Task] with Fs2Streams[Task]] {
    override def apply(takeWithin: FiniteDuration) = request => {
      def parseEvents(stream: fs2.Stream[Task, ServerSentEvent]): Task[Json] = {
        stream.compile.toList.map(events => Json.fromValues(events.map(_.asJson)))
      }

      request.response(asStream(Fs2Streams[Task])(stream => parseEvents(stream.through(Fs2ServerSentEvents.parse).interruptAfter(takeWithin))))
    }
  }

  private val monixStreamHandler: StreamHandler[MonixStreams with Effect[Task]] = new StreamHandler[MonixStreams with Effect[Task]] {
    override def apply(takeWithin: FiniteDuration) = request => {
      def parseEvents(stream: Observable[ServerSentEvent]): Task[Json] = {
        stream
          .foldLeftL(Vector.empty[ServerSentEvent])(_ :+ _)
          .map(events => Json.fromValues(events.map(_.asJson)))
      }

      request.response(asStream(MonixStreams)(stream => parseEvents(stream.transform(MonixServerSentEvents.parse).takeByTimespan(takeWithin))))
    }
  }

  def apply(backend: BackendOption)(
    addAcceptGzipByDefault: Boolean,
    disableCertificateVerification: Boolean,
    followRedirect: Boolean)(implicit scheduler: Scheduler): Http4sClient[_, _] = backend match {
    case Https4BackendOption =>
      (for {
        clientWithShutdown <- blazeClientBuilder(disableCertificateVerification)
          .allocated
          .map {
            case (client, shutdown) =>
              val c1 = if (addAcceptGzipByDefault) GZip()(client) else client
              val c2 = if (followRedirect) FollowRedirect(maxRedirects = 10)(client = c1) else c1
              c2 -> shutdown
          }
        (client, shutdown) = clientWithShutdown
        http4sBackend: SttpBackend[Task, Fs2Streams[Task]] = Http4sBackend.usingClient(client, blocker)
        streamHandler: StreamHandler[Effect[Task] with Fs2Streams[Task]] = fs2StreamHandler
      } yield new Http4sClient(HttpClientBackend(http4sBackend, streamHandler, shutdown))).runSyncUnsafe(10.seconds)

    case AsyncHttpOption =>
      val config = new DefaultAsyncHttpClientConfig.Builder()
        .setRequestTimeout(defaultHighTimeout.toMillis.toInt)
        .setPooledConnectionIdleTimeout(defaultHighTimeout.toMillis.toInt)
        .setMaxConnections(maxConnections)
        .setSslContext(new JdkSslContext(
          createSslContext(disableCertificateVerification),
          false,
          null,
          IdentityCipherSuiteFilter.INSTANCE,
          ApplicationProtocolConfig.DISABLED,
          ClientAuth.NONE,
          null,
          false))
        .setCompressionEnforced(addAcceptGzipByDefault)
        .build()

      val asyncHttpClient: AsyncHttpClient = new DefaultAsyncHttpClient(config)
      val backend = AsyncHttpClientMonixBackend.usingClient(asyncHttpClient)
      new Http4sClient(HttpClientBackend(backend, monixStreamHandler, backend.close()))
  }
}