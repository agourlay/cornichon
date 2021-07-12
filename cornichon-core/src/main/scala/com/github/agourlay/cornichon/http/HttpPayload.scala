package com.github.agourlay.cornichon.http

import java.net.URL

import cats.Show
import cats.effect.Blocker
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.{ Encoder, Json }
import monix.eval.Task
import org.http4s.{ EntityEncoder, MediaType, UrlForm }
import org.http4s.EntityEncoder._
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{ Multipart, Part }

trait HttpPayload[DSL, ENTITY] {
  implicit val entityEncoder: EntityEncoder[Task, ENTITY]
  def toEntity(a: DSL): Either[CornichonError, ENTITY]
}

object HttpPayload {

  // TODO inject from outside ??
  private val blocker = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)

  // anything with an Encoder String and derived Encoder for case classes
  implicit def fromCirceEncoderHttpPayload[A: Show: Encoder] = new HttpPayload[A, Json] {
    import org.http4s.circe._
    implicit val entityEncoder: EntityEncoder[Task, Json] = jsonEncoderOf[Task, Json]
    def toEntity(a: A): Either[CornichonError, Json] = CornichonJson.parseDslJson(a)
  }

  implicit val urlEncodedFormHttpPayload = new HttpPayload[List[(String, String)], UrlForm] {
    implicit val entityEncoder: EntityEncoder[Task, UrlForm] = UrlForm.entityEncoder[Task]
    def toEntity(a: List[(String, String)]) = Right(UrlForm.apply(a: _*))
  }

  implicit val formDataHttpPayload = new HttpPayload[List[(String, String)], Multipart[Task]] {
    implicit val entityEncoder: EntityEncoder[Task, Multipart[Task]] = multipartEncoder[Task]
    def toEntity(l: List[(String, String)]) =
      Right(
        Multipart[Task](
          l.iterator
            .map { case (name, value) => Part.formData[Task](name, value) }
            .toVector
        )
      )
  }

  implicit val formFileDataHttpPayload = new HttpPayload[URL, Multipart[Task]] {
    implicit val entityEncoder: EntityEncoder[Task, Multipart[Task]] = multipartEncoder[Task]
    def toEntity(url: URL) =
      Right(
        Multipart[Task](
          Vector(
            Part.fileData[Task](url.getFile, url, blocker, `Content-Type`(MediaType.image.jpeg))
          )
        )
      )
  }
}
