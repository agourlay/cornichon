package com.github.agourlay.cornichon.http

import java.nio.file.Path

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.util.Printing._
import com.github.agourlay.cornichon.resolver.Resolvable
import io.circe.Json
import org.http4s.UrlForm

import scala.concurrent.duration.FiniteDuration
import scala.xml.Elem

case class HttpMethod(name: String)

object HttpMethods {
  val DELETE = HttpMethod("DELETE")
  val GET = HttpMethod("GET")
  val HEAD = HttpMethod("HEAD")
  val OPTIONS = HttpMethod("OPTIONS")
  val PATCH = HttpMethod("PATCH")
  val POST = HttpMethod("POST")
  val PUT = HttpMethod("PUT")
}

trait BaseRequest {
  def url: String
  def params: Seq[(String, String)]
  def headers: Seq[(String, String)]

  def compactDescription: String

  def paramsTitle: String = if (params.isEmpty) "" else s" with query parameters ${printArrowPairs(params)}"
  def headersTitle: String = if (headers.isEmpty) "" else s" with headers ${printArrowPairs(headers)}"
}

case class DslHttpRequest[DSL_INPUT: Show: Resolvable, ENTITY_HTTP](method: HttpMethod, url: String, body: Option[DSL_INPUT], params: Seq[(String, String)], headers: Seq[(String, String)], hp: HttpPayload[DSL_INPUT, ENTITY_HTTP])
  extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  // Entity stays the same, only the DSL input can change.
  def withBody[OTHER_DSL_INPUT: Show: Resolvable](body: OTHER_DSL_INPUT)(implicit hp: HttpPayload[OTHER_DSL_INPUT, ENTITY_HTTP]) =
    copy(body = Some(body), hp = hp)

  lazy val compactDescription: String = {
    val base = s"${method.name} $url"
    val payloadTitle = body.fold("")(p => s" with body\n${p.show}")
    base + payloadTitle + paramsTitle + headersTitle
  }
}

trait DslHttpRequests {
  import com.github.agourlay.cornichon.http.HttpMethods._
  import com.github.agourlay.cornichon.http.HttpPayload._
  import cats.instances.string._
  import cats.instances.list._
  import cats.instances.tuple._

  // JSON
  def get(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](GET, url, None, Nil, Nil)
  def head(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](HEAD, url, None, Nil, Nil)
  def options(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](OPTIONS, url, None, Nil, Nil)
  def delete(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](DELETE, url, None, Nil, Nil)
  def post(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](POST, url, None, Nil, Nil)
  def put(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](PUT, url, None, Nil, Nil)
  def patch(url: String): DslHttpRequest[String, Json] = DslHttpRequest[String, Json](PATCH, url, None, Nil, Nil)

  // Form
  def postUrlEncodedForm(url: String): DslHttpRequest[List[(String, String)], UrlForm] = DslHttpRequest[List[(String, String)], UrlForm](POST, url, None, Nil, Nil)

  // XML
  implicit val showElem = Show.fromToString[Elem]
  def postXml(url: String): DslHttpRequest[Elem, Elem] = DslHttpRequest[Elem, Elem](POST, url, None, Nil, Nil)

  // File/Path
  implicit val showPath = Show.fromToString[Path]
  def uploadFile(url: String): DslHttpRequest[Path, Path] = DslHttpRequest[Path, Path](POST, url, None, Nil, Nil)
}

object DslHttpRequest extends DslHttpRequests {
  def apply[DSL_INPUT: Show: Resolvable, ENTITY_HTTP](method: HttpMethod, url: String, body: Option[DSL_INPUT], params: Seq[(String, String)], headers: Seq[(String, String)])(implicit hp: HttpPayload[DSL_INPUT, ENTITY_HTTP]): DslHttpRequest[DSL_INPUT, ENTITY_HTTP] =
    DslHttpRequest(method, url, body, params, headers, hp)
}

case class HttpStream(name: String) extends AnyVal

object HttpStreams {
  val SSE = HttpStream("Server-Sent-Event")
  val WS = HttpStream("WebSocket")
}

case class DslHttpStreamedRequest(stream: HttpStream, url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])
  extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  lazy val compactDescription: String = {
    val base = s"open ${stream.name} to $url"
    base + paramsTitle + headersTitle
  }
}

object DslHttpStreamedRequest {

  implicit val showStreamedRequest = new Show[DslHttpStreamedRequest] {
    def show(r: DslHttpStreamedRequest): String = {
      val params = if (r.params.isEmpty) "without parameters" else s"with parameters ${printArrowPairs(r.params)}"
      val headers = if (r.headers.isEmpty) "without headers" else s"with headers ${printArrowPairs(r.headers)}"

      s"""|${r.stream.name} request to ${r.url}
          |$params
          |$headers""".stripMargin
    }
  }

}