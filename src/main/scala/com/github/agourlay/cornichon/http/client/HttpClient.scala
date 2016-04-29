package com.github.agourlay.cornichon.http.client

import akka.http.scaladsl.model.HttpHeader
import cats.data.Xor
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpError }
import io.circe.Json

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def postJson(payload: Json, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def putJson(payload: Json, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def patchJson(payload: Json, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def deleteJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def getJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def headJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def optionsJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def openSSE(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def openWS(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def shutdown(): Future[Unit]
}