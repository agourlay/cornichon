package com.github.agourlay.cornichon.http

import java.nio.file.Path

import cats.Show
import cats.effect.Blocker
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.util.StringUtils
import io.circe.{ Encoder, Json }
import monix.eval.Task
import org.http4s.{ EntityEncoder, UrlForm }
import org.http4s.EntityEncoder._
import org.http4s.multipart.{ Multipart, Part }

import scala.xml.Elem

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

  implicit val formFileDataHttpPayload = new HttpPayload[Path, Multipart[Task]] {
    implicit val entityEncoder: EntityEncoder[Task, Multipart[Task]] = multipartEncoder[Task]
    def toEntity(p: Path) =
      Right(
        Multipart[Task](
          Vector(
            Part.fileData[Task](p.getFileName.toString, p.toFile, blocker)
          )
        )
      )
  }

  implicit val xmlHttpPayload = new HttpPayload[Elem, Elem] {
    import org.http4s.scalaxml._
    implicit val entityEncoder: EntityEncoder[Task, Elem] = xmlEncoder[Task]
    def toEntity(a: Elem) = Right(a)
  }

  implicit val xmlStringHttpPayload = new HttpPayload[String, Elem] {
    import org.http4s.scalaxml._
    implicit val entityEncoder: EntityEncoder[Task, Elem] = xmlEncoder[Task]
    def toEntity(a: String) = StringUtils.parseXml(a)
  }

  implicit val filePathHttpPayload = new HttpPayload[Path, Path] {
    implicit val entityEncoder: EntityEncoder[Task, Path] = filePathEncoder[Task](blocker)
    def toEntity(p: Path) = Right(p)
  }
}
