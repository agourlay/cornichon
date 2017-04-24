package com.github.agourlay.cornichon.core

import cats.Show
import cats.syntax.show._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._

import com.github.agourlay.cornichon.json.{ JsonPath, NotStringFieldError }
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.Json

import scala.collection.immutable.HashMap

case class Session(private val content: Map[String, Vector[String]]) {

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

  def getJsonStringField(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root) = {
    val res = for {
      json ← getJson(key, stackingIndice, path)
      field ← Either.fromOption(json.asString, NotStringFieldError(json, path))
    } yield field
    res.fold(ce ⇒ throw ce.toException, identity)
  }

  def getJsonOpt(key: String, stackingIndice: Option[Int] = None): Option[Json] = getOpt(key, stackingIndice).flatMap(s ⇒ parseJson(s).toOption)

  def getList(keys: Seq[String]): Either[CornichonError, List[String]] = keys.toList.traverseU(v ⇒ get(v))

  def getHistory(key: String): Vector[String] = content.getOrElse(key, Vector.empty)

  def addValue(key: String, value: String) =
    //FIXME turning this into a proper CornichonError creates a lot of work to handle the Either
    if (key.trim.isEmpty) throw EmptyKeyException(this).toException
    else
      content.get(key).fold(Session(content + (key → Vector(value)))) { values ⇒
        Session((content - key) + (key → values.:+(value)))
      }

  def addValues(tuples: Seq[(String, String)]) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))

  def removeKey(key: String) = Session(content - key)

  def merge(otherSession: Session) =
    copy(content = content ++ otherSession.content)

  val prettyPrint =
    content.toSeq
      .sortBy(_._1)
      .map(pair ⇒ pair._1 + " -> " + pair._2.toIterator.map(_.show).mkString("Values(", ", ", ")"))
      .mkString("\n")
}

object Session {
  def newEmpty = Session(HashMap.empty)
  implicit val showSession = new Show[Session] {
    def show(s: Session) = s.prettyPrint
  }
}

case class SessionKey(name: String, index: Option[Int] = None) {
  def atIndex(index: Int) = copy(index = Some(index))
}

case class EmptyKeyException(s: Session) extends CornichonError {
  val baseErrorMessage = s"key value can not be empty - session is \n${s.prettyPrint}"
}

case class KeyNotFoundInSession(key: String, indice: Option[Int], s: Session) extends CornichonError {
  val baseErrorMessage = s"key '$key'${indice.fold("")(i ⇒ s" at indice '$i'")} can not be found in session \n${s.prettyPrint}"
}