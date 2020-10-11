package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.util.Printing.printArrowPairs

case class HttpRequest[Body: Show](method: HttpMethod, url: String, body: Option[Body], params: Seq[(String, String)], headers: Seq[(String, String)])

object HttpRequest {
  implicit def showRequest[Body: Show]: Show[HttpRequest[Body]] = new Show[HttpRequest[Body]] {
    def show(r: HttpRequest[Body]): String = {
      val body = r.body.fold("without body")(b => s"with body\n${b.show}")
      val params = if (r.params.isEmpty) "without parameters" else s"with parameters ${printArrowPairs(r.params)}"
      val headers = if (r.headers.isEmpty) "without headers" else s"with headers ${printArrowPairs(r.headers)}"

      s"""|HTTP ${r.method.name} request to ${r.url}
          |$params
          |$headers
          |$body""".stripMargin
    }
  }
}