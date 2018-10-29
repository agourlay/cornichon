package com.github.agourlay.cornichon.http.server

import com.github.agourlay.cornichon.dsl.CloseableResource
import monix.eval.Task

trait HttpServer {
  def startServer(): Task[(String, CloseableResource)]
}
