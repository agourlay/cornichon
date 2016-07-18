package com.github.agourlay.cornichon.http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.ConnectionContext
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.{ HttpRequest ⇒ AkkaHttpRequest }
import akka.http.scaladsl.model.HttpHeader.ParsingResult

import cats.data.Xor
import cats.data.Xor.{ left, right }

import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.http.HttpMethod
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.json.CornichonJson._

import de.heikoseeberger.akkasse.EventStreamUnmarshalling._
import de.heikoseeberger.akkasse.ServerSentEvent
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeoutException
import javax.net.ssl._

import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class AkkaHttpClient(implicit system: ActorSystem, mat: Materializer, executionContext: ExecutionContext) extends HttpClient {

  // Disable JDK built-in checks
  // https://groups.google.com/forum/#!topic/akka-user/ziI1fPBtxV8
  // hostName verification still enabled by default
  // use "ssl-config.loose.disableHostnameVerification = true" to disable it
  private val sslContext = {
    val ssl = SSLContext.getInstance("SSL")
    val byPassTrustManagers = Array[TrustManager](new X509TrustManager() {
      override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = ()
      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = ()
    })
    ssl.init(null, byPassTrustManagers, new SecureRandom)
    ConnectionContext.https(ssl)
  }

  def httpMethodMapper(method: HttpMethod) = method match {
    case DELETE  ⇒ Delete
    case GET     ⇒ Get
    case HEAD    ⇒ Head
    case OPTIONS ⇒ Options
    case PATCH   ⇒ Patch
    case POST    ⇒ Post
    case PUT     ⇒ Put
  }

  def parseHttpHeaders(headers: Seq[(String, String)]): Xor[MalformedHeadersError, Seq[HttpHeader]] = {
    @tailrec
    def loop(headers: Seq[(String, String)], acc: Seq[HttpHeader]): Xor[MalformedHeadersError, Seq[HttpHeader]] =
      if (headers.isEmpty) right(acc)
      else {
        val (name, value) = headers.head
        HttpHeader.parse(name, value) match {
          case ParsingResult.Ok(h, e) ⇒ loop(headers.tail, acc :+ h)
          case ParsingResult.Error(e) ⇒ left(MalformedHeadersError(e.formatPretty))
        }
      }

    loop(headers, Seq.empty[HttpHeader])
  }

  override def runRequest(method: HttpMethod, url: String, payload: Option[Json], params: Seq[(String, String)], headers: Seq[(String, String)], timeout: FiniteDuration) = {
    val requestBuilder = httpMethodMapper(method)
    parseHttpHeaders(headers).flatMap { akkaHeaders ⇒
      val request = requestBuilder(uriBuilder(url, params), payload).withHeaders(collection.immutable.Seq(akkaHeaders: _*))
      val f = Http().singleRequest(request, sslContext).flatMap(toCornichonResponse)
      waitForRequestFuture(request, f, timeout)
    }
  }

  private def waitForRequestFuture(initialRequest: AkkaHttpRequest, f: Future[Xor[HttpError, CornichonHttpResponse]], t: FiniteDuration): Xor[HttpError, CornichonHttpResponse] =
    Try { Await.result(f, t) } match {
      case Success(s) ⇒ s
      case Failure(failure) ⇒ failure match {
        case e: TimeoutException ⇒ left(TimeoutError(e.getMessage, initialRequest.getUri().toString))
        case t: Throwable        ⇒ left(RequestError(t, initialRequest.getUri().toString))
      }
    }

  implicit def JsonMarshaller: ToEntityMarshaller[Json] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(j ⇒ j.noSpaces)

  private def uriBuilder(url: String, params: Seq[(String, String)]): Uri = Uri(url).withQuery(Query(params: _*))

  override def openStream(stream: HttpStream, url: String, params: Seq[(String, String)], headers: Seq[(String, String)], takeWithin: FiniteDuration) = {
    parseHttpHeaders(headers).flatMap { akkaHeaders ⇒
      stream match {
        case SSE ⇒ openSSE(url, params, akkaHeaders, takeWithin)
        case WS  ⇒ openWS(url, params, akkaHeaders, takeWithin)
      }
    }
  }

  def openSSE(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration) = {
    val request = Get(uriBuilder(url, params)).withHeaders(collection.immutable.Seq(headers: _*))
    val f = Http().singleRequest(request, sslContext)
      .flatMap(expectSSE)
      .map { sse ⇒
        sse.map { source ⇒
          val r = source
            .takeWithin(takeWithin)
            .filter(_.data.nonEmpty)
            .runFold(Vector.empty[ServerSentEvent])(_ :+ _)
            .map { events ⇒
              CornichonHttpResponse(
                status = StatusCodes.OK.intValue,
                headers = Seq.empty,
                body = prettyPrint(Json.fromValues(events.map(_.asJson).toList))
              )
            }
          Await.result(r, takeWithin)
        }
      }
    waitForRequestFuture(request, f, takeWithin)
  }

  // TODO implement WS support
  def openWS(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration): Xor[HttpError, CornichonHttpResponse] = {
    val uri = uriBuilder(url, params)
    val req = WebSocketRequest(uri).copy(extraHeaders = collection.immutable.Seq(headers: _*))

    val received = ListBuffer.empty[String]

    /*val incoming: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict ⇒
          received += message.text
        case _ ⇒ ()
      }

    val flow = Flow.fromSinkAndSourceMat(incoming, Source.empty[Message])(Keep.left)

    val (upgradeResponse, closed) = Http().singleWebSocketRequest(req, flow, sslContext)

    val responses = upgradeResponse.map { upgrade ⇒
      if (upgrade.response.status == StatusCodes.OK) right(StatusCodes.OK)
      else left(WsUpgradeError(upgrade.response.status.intValue()))
    }

    Thread.sleep(takeWithin.toMillis)
    responses.value.fold(throw TimeoutError("Websocket connection did not complete in time", url)) {
      case Failure(e) ⇒ throw e
      case Success(s) ⇒ s.map { _ ⇒
        CornichonHttpResponse(
          status = StatusCodes.OK,
          headers = collection.immutable.Seq.empty[HttpHeader],
          body = prettyPrint(Json.fromValues(received.map(_.asJson).toList))
        )
      }
    }*/
    ???
  }

  private def expectSSE(httpResponse: HttpResponse): Future[Xor[HttpError, Source[ServerSentEvent, Any]]] =
    Unmarshal(Gzip.decode(httpResponse)).to[Source[ServerSentEvent, Any]].map { sse ⇒
      right(sse)
    }.recover {
      case e: Exception ⇒ left(SseError(e))
    }

  private def toCornichonResponse(httpResponse: HttpResponse): Future[Xor[HttpError, CornichonHttpResponse]] =
    Unmarshal(Gzip.decode(httpResponse)).to[String].map { body: String ⇒
      right(
        CornichonHttpResponse(
          status = httpResponse.status.intValue(),
          headers = httpResponse.headers.map(h ⇒ (h.name, h.value)),
          body = body
        )
      )
    }.recover {
      case e: Exception ⇒
        left(UnmarshallingResponseError(e, httpResponse.toString()))
    }

  override def shutdown() = Http().shutdownAllConnectionPools()

  override def paramsFromUrl(url: String): Seq[(String, String)] = Query(url)

}