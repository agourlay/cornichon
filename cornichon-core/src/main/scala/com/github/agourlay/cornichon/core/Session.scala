package com.github.agourlay.cornichon.core

import cats.Show
import cats.syntax.show._
import cats.syntax.monoid._
import cats.instances.string._
import cats.instances.map._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import cats.kernel.Monoid

import com.github.agourlay.cornichon.json.{ JsonPath, NotStringFieldError }
import com.github.agourlay.cornichon.json.CornichonJson._

import io.circe.Json

import scala.collection.immutable.HashMap

case class Session(private val content: Map[String, Vector[String]]) extends AnyVal {

  def getOpt(key: String, stackingIndice: Option[Int] = None): Option[String] =
    for {
      values ← content.get(key)
      value ← stackingIndice.fold(values.lastOption) { indice ⇒ values.lift(indice) }
    } yield value

  def getUnsafe(key: String, stackingIndice: Option[Int] = None): String =
    get(key, stackingIndice).fold(ce ⇒ throw ce.toException, identity)

  def getUnsafe(sessionKey: SessionKey): String = getUnsafe(sessionKey.name, sessionKey.index)

  def get(key: String, stackingIndice: Option[Int] = None): Either[CornichonError, String] =
    Either.fromOption(getOpt(key, stackingIndice), KeyNotFoundInSession(key, stackingIndice, this))

  def get(sessionKey: SessionKey): Either[CornichonError, String] = get(sessionKey.name, sessionKey.index)

  def getJson(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root): Either[CornichonError, Json] =
    for {
      sessionValue ← get(key, stackingIndice)
      jsonValue ← parseJson(sessionValue)
      extracted ← JsonPath.run(path, jsonValue)
    } yield extracted

  def getJsonUnsafe(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root) =
    getJson(key, stackingIndice, path).fold(ce ⇒ throw ce.toException, identity)

  def getJsonStringField(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root) =
    for {
      json ← getJson(key, stackingIndice, path)
      field ← Either.fromOption(json.asString, NotStringFieldError(json, path))
    } yield field

  def getJsonStringFieldUnsafe(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root) =
    getJsonStringField(key, stackingIndice, path).fold(ce ⇒ throw ce.toException, identity)

  def getJsonOpt(key: String, stackingIndice: Option[Int] = None): Option[Json] = getOpt(key, stackingIndice).flatMap(s ⇒ parseJson(s).toOption)

  def getList(keys: Seq[String]): Either[CornichonError, List[String]] = keys.toList.traverseU(v ⇒ get(v))

  def getHistory(key: String): Vector[String] = content.getOrElse(key, Vector.empty)

  //FIXME turning this into a proper CornichonError creates a lot of work to handle the Either
  def addValue(key: String, value: String) = {
    val trimmedKey = key.trim
    if (trimmedKey.isEmpty)
      throw EmptyKeyException(this).toException
    else if (Session.notAllowedInKey.exists(forbidden ⇒ trimmedKey.contains(forbidden)))
      throw IllegalKeyException.toException
    else
      content.get(key).fold(Session(content + (key → Vector(value)))) { values ⇒
        Session((content - key) + (key → values.:+(value)))
      }
  }

  def addValues(tuples: (String, String)*) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))

  def removeKey(key: String) = Session(content - key)
}

object Session {
  val newEmpty = Session(HashMap.empty)

  val notAllowedInKey = "\r\n<>/[] "

  implicit val monoidSession = new Monoid[Session] {
    def empty: Session = newEmpty
    def combine(x: Session, y: Session): Session =
      Session(x.content.combine(y.content))
  }

  implicit val showSession = Show.show[Session] { s ⇒
    s.content.toSeq
      .sortBy(_._1)
      .map(pair ⇒ pair._1 + " -> " + pair._2.toIterator.map(_.show).mkString("Values(", ", ", ")"))
      .mkString("\n")
  }
}

case class SessionKey(name: String, index: Option[Int] = None) {
  def atIndex(index: Int) = copy(index = Some(index))
}

case class EmptyKeyException(s: Session) extends CornichonError {
  val baseErrorMessage = s"key can not be empty - session is \n${s.show}"
}

object IllegalKeyException extends CornichonError {
  val baseErrorMessage = s"key can not contain chars '${Session.notAllowedInKey}'"
}

case class KeyNotFoundInSession(key: String, indice: Option[Int], s: Session) extends CornichonError {
  val baseErrorMessage = s"key '$key'${indice.fold("")(i ⇒ s" at indice '$i'")} can not be found in session \n${s.show}"
}