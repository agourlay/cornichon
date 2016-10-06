package com.github.agourlay.cornichon.http.server

import com.github.agourlay.cornichon.dsl.CloseableResource

import scala.concurrent.Future

trait HttpServer {
  def startServer: Future[(String, CloseableResource)]
}
