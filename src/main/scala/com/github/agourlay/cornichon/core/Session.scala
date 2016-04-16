package com.github.agourlay.cornichon.core

import cats.data.Xor
import com.github.agourlay.cornichon.json.CornichonJson

import scala.collection.immutable.HashMap

case class Session(content: Map[String, Vector[String]]) extends CornichonJson {

  def getOpt(key: String, stackingIndice: Option[Int] = None): Option[String] = {

    def valueExtractor(stackingIndice: Option[Int], values: Vector[String]) =
      stackingIndice.fold(values.lastOption) { indice ⇒
        values.lift(indice)
      }

    for {
      values ← content.get(key)
      value ← valueExtractor(stackingIndice, values)
    } yield value

  }

  def get(key: String, stackingIndice: Option[Int] = None): String = getOpt(key, stackingIndice).getOrElse(throw new KeyNotFoundInSession(key, this))

  def getXor(key: String, stackingIndice: Option[Int] = None) = Xor.fromOption(getOpt(key, stackingIndice), KeyNotFoundInSession(key, this))

  def getJson(key: String, stackingIndice: Option[Int] = None) = parseJson(get(key, stackingIndice))

  def getJsonOpt(key: String, stackingIndice: Option[Int] = None) = getOpt(key, stackingIndice).map(parseJson)

  def getList(keys: Seq[String]) = keys.map(v ⇒ get(v))

  def getHistory(key: String) = content.getOrElse(key, Vector.empty)

  def addValue(key: String, value: String) =
    if (key.trim.isEmpty) throw new EmptyKeyException(this)
    else
      content.get(key).fold(Session(content + (key → Vector(value)))) { values ⇒
        Session((content - key) + (key → values.:+(value)))
      }

  def addValues(tuples: Seq[(String, String)]) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))

  def removeKey(key: String) = Session(content - key)

  def merge(otherSession: Session) =
    copy(content = content ++ otherSession.content)

  val prettyPrint = content.toSeq.sortBy(_._1).map(pair ⇒ pair._1 + " -> " + pair._2).mkString("\n")

}

object Session {
  def newSession = Session(HashMap.empty)
}