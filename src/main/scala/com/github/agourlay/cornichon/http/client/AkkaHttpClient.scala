package com.github.agourlay.cornichon.http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.ConnectionContext
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.NotUsed
import cats.syntax.either._
import cats.syntax.show._
import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.http.HttpMethod
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.core.CornichonError
import de.heikoseeberger.akkasse.EventStreamUnmarshalling._
import de.heikoseeberger.akkasse.ServerSentEvent
import de.heikoseeberger.akkasse.MediaTypes.`text/event-stream`
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._

import akka.http.scaladsl.model.headers.Accept
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext, Future }

class AkkaHttpClient(implicit system: ActorSystem, executionContext: ExecutionContext, mat: ActorMaterializer) extends HttpClient {

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

  def parseHttpHeaders(headers: Seq[(String, String)]): Either[MalformedHeadersError, Seq[HttpHeader]] = {
    @tailrec
    def loop(headers: Seq[(String, String)], acc: Seq[HttpHeader]): Either[MalformedHeadersError, Seq[HttpHeader]] =
      if (headers.isEmpty) Right(acc)
      else {
        val (name, value) = headers.head
        HttpHeader.parse(name, value) match {
          case ParsingResult.Ok(h, _) ⇒ loop(headers.tail, acc :+ h)
          case ParsingResult.Error(e) ⇒ Left(MalformedHeadersError(e.formatPretty))
        }
      }

    loop(headers, Seq.empty[HttpHeader])
  }

  override def runRequest(
    method: HttpMethod,
    url: String,
    payload: Option[Json],
    params: Seq[(String, String)],
    headers: Seq[(String, String)]
  ): Future[Either[CornichonError, CornichonHttpResponse]] = {
    val requestBuilder = httpMethodMapper(method)
    parseHttpHeaders(headers).fold(
      mh ⇒ Future.successful(Left(mh)),
      akkaHeaders ⇒ {
        val request = requestBuilder(uriBuilder(url, params), payload).withHeaders(collection.immutable.Seq(akkaHeaders: _*))
        Http().singleRequest(request, sslContext).flatMap(toCornichonResponse)
      }
    )
  }

  implicit def JsonMarshaller: ToEntityMarshaller[Json] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(j ⇒ j.noSpaces)

  private def uriBuilder(url: String, params: Seq[(String, String)]): Uri = Uri(url).withQuery(Query(params: _*))

  override def openStream(stream: HttpStream, url: String, params: Seq[(String, String)], headers: Seq[(String, String)], takeWithin: FiniteDuration) = {
    parseHttpHeaders(headers).fold(
      mh ⇒ Future.successful(Left(mh)),
      akkaHeaders ⇒ {
        stream match {
          case SSE ⇒ openSSE(url, params, akkaHeaders, takeWithin)
          case WS  ⇒ openWS(url, params, akkaHeaders, takeWithin)
        }
      }
    )
  }

  def openSSE(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration) = {
    val request = Get(uriBuilder(url, params))
      .withHeaders(collection.immutable.Seq(headers: _*))
      .addHeader(Accept(`text/event-stream`))

    Http().singleRequest(request, sslContext)
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
                body = Json.fromValues(events.map(_.asJson).toList).show
              )
            }
          Await.result(r, takeWithin)
        }
      }
  }

  // TODO implement WS support
  def openWS(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration): Future[Either[HttpError, CornichonHttpResponse]] = {
    /*val uri = uriBuilder(url, params)
    val req = WebSocketRequest(uri).copy(extraHeaders = collection.immutable.Seq(headers: _*))

    val received = ListBuffer.empty[String]

    val incoming: Sink[Message, Future[Done]] =
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

  private def expectSSE(httpResponse: HttpResponse): Future[Either[HttpError, Source[ServerSentEvent, NotUsed]]] =
    Unmarshal(Gzip.decode(httpResponse)).to[Source[ServerSentEvent, NotUsed]].map { sse ⇒
      Right(sse)
    }.recover {
      case e: Exception ⇒ Left(SseError(e))
    }

  private def toCornichonResponse(httpResponse: HttpResponse): Future[Either[CornichonError, CornichonHttpResponse]] =
    Unmarshal(Gzip.decode(httpResponse)).to[String].map { body: String ⇒
      Right(
        CornichonHttpResponse(
          status = httpResponse.status.intValue(),
          headers = httpResponse.headers.map(h ⇒ (h.name, h.value)),
          body = body
        )
      )
    }.recover {
      case e: Exception ⇒
        Left(UnmarshallingResponseError(e, httpResponse.toString()))
    }

  override def shutdown() = Http().shutdownAllConnectionPools()

  override def paramsFromUrl(url: String): Seq[(String, String)] = Query(url)

}