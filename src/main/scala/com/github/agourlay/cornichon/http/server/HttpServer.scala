package com.github.agourlay.cornichon.http.server

import scala.concurrent.Future

trait HttpServer {

  def startServer: Future[Unit]
  def stopServer: Future[Unit]

}
