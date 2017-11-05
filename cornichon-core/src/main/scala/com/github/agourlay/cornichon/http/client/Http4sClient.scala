package com.github.agourlay.cornichon.http.client

import cats.data.EitherT
import cats.syntax.either._
import cats.instances.future._
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.http.HttpService._
import fs2.{ Scheduler, Strategy, Task }
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.client.blaze.{ BlazeClientConfig, PooledHttp1Client }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.collection.breakOut
import ExecutionContext.Implicits.global

// TODO Gzip support https://github.com/http4s/http4s/issues/1327
class Http4sClient extends HttpClient {

  implicit val strategy = Strategy.fromExecutionContext(ExecutionContext.Implicits.global)
  implicit val scheduler = Scheduler.fromFixedDaemonPool(1)

  private val httpClient = PooledHttp1Client(
    maxTotalConnections = 100,
    config = BlazeClientConfig.insecure.copy(
      idleTimeout = Duration.Inf,
      responseHeaderTimeout = Duration.Inf,
      requestTimeout = Duration.Inf
    )
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

  def buildHeaders(headers: Seq[(String, String)]): Headers = {
    val h: List[Header] = headers.map { case (n, v) ⇒ Header(n, v).parsed }(breakOut)
    Headers(h)
  }

  def addQueryParams(uri: Uri, moreParams: Seq[(String, String)]): Uri =
    if (moreParams.isEmpty)
      uri
    else {
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
          headers = response.headers.map(h ⇒ (h.name.value, h.value))(breakOut),
          body = decodedBody
        )
      }
  }

  override def runRequest(cReq: HttpRequest[Json], t: FiniteDuration): EitherT[Future, CornichonError, CornichonHttpResponse] =
    Uri.fromString(cReq.url).fold[EitherT[Future, CornichonError, CornichonHttpResponse]](
      e ⇒ EitherT.left(Future.successful(MalformedUriError(cReq.url, e.message))),
      uri ⇒ EitherT {
        val r = Request(httpMethodMapper(cReq.method))
          .withHeaders(buildHeaders(cReq.headers))
          .withUri(addQueryParams(uri, cReq.params))

        cReq.body
          .fold(Task.now(r))(b ⇒ r.withBody(b))
          .flatMap(r ⇒ httpClient.fetch(r)(handleResponse))
          .map(_.asRight)
          .race(Task.schedule(TimeoutErrorAfter(cReq, t).asLeft, t))
          .map(_.fold(identity, identity))
          .unsafeRunAsyncFuture()
          .recover {
            case t: Throwable ⇒ RequestError(cReq, t).asLeft
          }
      }
    )

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) = ???

  def shutdown() = httpClient.shutdown.map(_ ⇒ Done).unsafeRunAsyncFuture()

  def paramsFromUrl(url: String) =
    if (url.contains('?'))
      Uri.fromString(url).map(_.params.toList).leftMap(e ⇒ MalformedUriError(url, e.message))
    else
      rightNil
}
