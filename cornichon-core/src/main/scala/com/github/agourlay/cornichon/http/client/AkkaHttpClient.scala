package com.github.agourlay.cornichon.http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.NotUsed
import cats.syntax.either._
import cats.syntax.show._
import cats.instances.future._
import cats.data.EitherT
import cats.Show
import cats.instances.string._
import com.github.agourlay.cornichon.http._
import com.github.agourlay.cornichon.http.HttpMethod
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException, Done }
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpRequest }
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._

import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.Task

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }

class AkkaHttpClient(ec: ExecutionContext) extends HttpClient {

  // Prevent an internal log WARN from AKKA
  java.security.Security.setProperty("networkaddress.cache.ttl", "30")

  implicit private val system = ActorSystem("cornichon-actor-system")
  implicit private val mat = ActorMaterializer()
  implicit private val iec = ec

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
    case other   ⇒ throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  def parseHttpHeaders(headers: Seq[(String, String)]): Either[MalformedHeadersError, Seq[HttpHeader]] = {
    @tailrec
    def loop(headers: Seq[(String, String)], acc: Seq[HttpHeader]): Either[MalformedHeadersError, Seq[HttpHeader]] =
      headers match {
        case Nil ⇒
          Right(acc.reverse)
        case head :: tail ⇒
          val (name, value) = head
          HttpHeader.parse(name, value) match {
            case ParsingResult.Ok(h, _) ⇒ loop(tail, h +: acc)
            case ParsingResult.Error(e) ⇒ Left(MalformedHeadersError(e.formatPretty))
          }
      }

    loop(headers, Nil)
  }

  override def runRequest(req: HttpRequest[Json], t: FiniteDuration): EitherT[Task, CornichonError, CornichonHttpResponse] =
    EitherT(Task.deferFuture(runSingleRequest(req, t)(toCornichonResponse).value))

  private def runSingleRequest[A: Show: ToEntityMarshaller, B](req: HttpRequest[A], t: FiniteDuration)(unmarshaller: HttpResponse ⇒ Future[Either[CornichonError, B]]): EitherT[Future, CornichonError, B] =
    parseHttpHeaders(req.headers).fold(
      mh ⇒ EitherT.fromEither[Future](mh.asInstanceOf[CornichonError].asLeft[B]),
      akkaHeaders ⇒ {
        val requestBuilder = httpMethodMapper(req.method)
        val uri = uriBuilder(req.url, req.params)
        val request = requestBuilder(uri, req.body).withHeaders(collection.immutable.Seq(akkaHeaders: _*))
        val response = Http().singleRequest(request, sslContext)
        for {
          resp ← EitherT(handleRequest(t, req)(response))
          cornichonResp ← EitherT(unmarshaller(resp))
        } yield cornichonResp
      }
    )

  implicit def JsonMarshaller: ToEntityMarshaller[Json] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(j ⇒ j.noSpaces)

  private def uriBuilder(url: String, params: Seq[(String, String)]): Uri = Uri(url).withQuery(Query(params: _*))

  private def handleRequest[A: Show](t: FiniteDuration, request: HttpRequest[A])(resp: Future[HttpResponse]): Future[Either[CornichonError, HttpResponse]] = {
    val p = Promise[Either[CornichonError, HttpResponse]]()
    val timeoutCancellable = system.scheduler.scheduleOnce(t)(p.trySuccess(Left(TimeoutErrorAfter(request, t))))

    resp.onComplete {
      case Success(res) ⇒
        timeoutCancellable.cancel()
        try p.success(res.asRight[CornichonError]) catch {
          case _: Throwable ⇒ res.discardEntityBytes() // we discard the databytes explicitly, to avoid stalling the connection
        }
      case Failure(failure) ⇒
        timeoutCancellable.cancel()
        p.trySuccess(RequestError(request, failure).asLeft)
    }

    p.future
  }

  override def openStream(req: HttpStreamedRequest, t: FiniteDuration) =
    req.stream match {
      case SSE ⇒ Task.deferFuture(openSSE(req, t).value)
      case _   ⇒ ??? // TODO implement WS support
    }

  def openSSE(req: HttpStreamedRequest, connectionTimeout: FiniteDuration) = {

    val request = HttpRequest[String](GET, req.url, None, req.params, req.headers).addHeaders("text" → "event-stream")

    runSingleRequest[String, Source[ServerSentEvent, NotUsed]](request, connectionTimeout)(expectSSE).map { source ⇒
      val r = source
        .takeWithin(req.takeWithin)
        .filter(_.data.nonEmpty)
        .runFold(Vector.empty[ServerSentEvent])(_ :+ _)
        .map { events ⇒
          CornichonHttpResponse(
            status = StatusCodes.OK.intValue,
            headers = Seq.empty,
            body = Json.fromValues(events.map(_.asJson)).show
          )
        }
      Await.result(r, req.takeWithin)
    }
  }

  private def expectSSE(httpResponse: HttpResponse): Future[Either[HttpError, Source[ServerSentEvent, NotUsed]]] =
    Unmarshal(Gzip.decodeMessage(httpResponse)).to[Source[ServerSentEvent, NotUsed]].map { sse ⇒
      Right(sse)
    }.recover {
      case e: Exception ⇒ Left(SseError(e))
    }

  private def toCornichonResponse(httpResponse: HttpResponse): Future[Either[CornichonError, CornichonHttpResponse]] =
    Unmarshal(Gzip.decodeMessage(httpResponse)).to[String].map { body: String ⇒
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

  override def shutdown() = {
    val f = for {
      _ ← Http().shutdownAllConnectionPools()
      _ ← Future.successful(mat.shutdown())
      _ ← system.terminate()
    } yield Done

    Task.deferFuture(f)
  }

  override def paramsFromUrl(url: String): Either[CornichonError, Seq[(String, String)]] =
    if (url.contains('?'))
      Either.catchNonFatal(Query(url.split('?')(1)))
        .leftMap(e ⇒ MalformedUriError(url, e.getMessage))
    else
      rightNil

}