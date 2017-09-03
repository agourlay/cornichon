package com.github.agourlay.cornichon.http.client

import java.util.concurrent.TimeUnit

import cats.data.EitherT
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http._
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixHandler
import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._
import io.circe.Json
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import okhttp3.{ Dispatcher, OkHttpClient }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class OkHttpMonixClient extends HttpClient {

  val dispatcher = new Dispatcher()
  dispatcher.setMaxRequests(100)
  dispatcher.setMaxRequestsPerHost(100)

  val client = new OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .writeTimeout(0, TimeUnit.MILLISECONDS)
    .dispatcher(dispatcher)
    .build()

  implicit val sttpHandler = OkHttpMonixHandler(client)

  def runRequest(req: HttpRequest[Json], t: FiniteDuration) = {
    val uri: Uri = uri"${req.url}?${req.params}"
    val sttpReq = req
      .body
      .fold(sttp)(b ⇒ sttp.body(b))
      .headers(req.headers: _*)

    val sttpReqFinal = req.method match {
      case DELETE  ⇒ sttpReq.delete(uri)
      case GET     ⇒ sttpReq.get(uri)
      case HEAD    ⇒ sttpReq.head(uri)
      case OPTIONS ⇒ sttpReq.options(uri)
      case PATCH   ⇒ sttpReq.patch(uri)
      case POST    ⇒ sttpReq.post(uri)
      case PUT     ⇒ sttpReq.put(uri)
      case other   ⇒ throw CornichonException(s"unsupported HTTP method ${other.name}")
    }

    EitherT[Future, CornichonError, CornichonHttpResponse] {
      sttpReqFinal
        .send()
        .map(_.asRight)
        .timeoutTo(t, Task.delay(TimeoutErrorAfter(req, t).asLeft))
        .map(_.map(handleResponse))
        .runAsync
        .recover {
          case t: Throwable ⇒ RequestError(req, t).asLeft
        }
    }
  }

  def handleResponse[A](response: Response[String]) =
    CornichonHttpResponse(
      status = response.code,
      headers = response.headers,
      body = response.body.fold(identity, identity)
    )

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) = ???

  def shutdown() = {
    dispatcher.executorService().shutdownNow()
    Done.futureDone
  }

  def paramsFromUrl(url: String) = uri"$url".paramsSeq
}
