package com.github.agourlay.cornichon.http.client

import akka.http.scaladsl.model.HttpHeader
import cats.data.Xor
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpError }
import org.json4s._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def postJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]]

  def putJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]]

  def deleteJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]]

  def getJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]]

  def getSSE(url: String, params: Seq[(String, String)], takeWithin: FiniteDuration, headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]]

  def getWS(url: String, params: Seq[(String, String)], takeWithin: FiniteDuration, headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]]

  def shutdown(): Future[Unit]
}
