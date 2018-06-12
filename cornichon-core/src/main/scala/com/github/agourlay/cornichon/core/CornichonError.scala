package com.github.agourlay.cornichon.core

import java.io.{ PrintWriter, StringWriter }

import cats.data.{ EitherT, NonEmptyList }
import cats.syntax.either._
import cats.instances.future._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NoStackTrace

trait CornichonError {
  def baseErrorMessage: String
  val causedBy: Option[NonEmptyList[CornichonError]] = None

  lazy val renderedMessage: String = causedBy.fold(baseErrorMessage) { causes ⇒
    s"""$baseErrorMessage
       |caused by:
       |${causes.toList.map(c ⇒ c.renderedMessage).mkString("\nand\n")}""".stripMargin
  }

  def toException = CornichonException(renderedMessage)
}

object CornichonError {
  def genStacktrace(exception: Throwable): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    exception.printStackTrace(pw)
    sw.toString
  }

  def fromString(error: String): CornichonError =
    BasicError(error)

  def fromThrowable(exception: Throwable): CornichonError =
    StepExecutionError(exception)

  def catchThrowable[A](f: ⇒ A): Either[CornichonError, A] =
    Either.catchNonFatal(f).leftMap(fromThrowable)

  implicit class fromEither[A](e: Either[CornichonError, A]) {
    def valueUnsafe: A = e.fold(e ⇒ throw e.toException, identity)
    def futureEitherT(implicit ec: ExecutionContext): EitherT[Future, CornichonError, A] = EitherT.fromEither[Future](e)
  }
}

case class StepExecutionError(e: Throwable) extends CornichonError {
  lazy val baseErrorMessage = s"exception thrown ${CornichonError.genStacktrace(e)}"
}

case class BasicError(error: String) extends CornichonError {
  lazy val baseErrorMessage = error
}

case class CornichonException(m: String) extends Exception with NoStackTrace {
  override def getMessage = m
}