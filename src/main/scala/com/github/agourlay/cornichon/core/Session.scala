package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.json.CornichonJson

import scala.collection.immutable.HashMap

case class Session(content: HashMap[String, Vector[String]]) {

  val json = new CornichonJson()

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

  def getJson(key: String, stackingIndice: Option[Int] = None) = json.parseJson(get(key, stackingIndice))

  def getList(keys: Seq[String]) = keys.map(v ⇒ get(v))

  def addValue(key: String, value: String) =
    content.get(key).fold(Session(content + (key → Vector(value)))) { values ⇒
      Session((content - key) + (key → values.:+(value)))
    }

  def addValues(tuples: Seq[(String, String)]) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))

  def removeKey(key: String) = Session(content - key)

  val prettyPrint = content.toSeq.sortBy(_._1).map(pair ⇒ pair._1 + " -> " + pair._2).mkString("\n")

}

object Session {
  def newSession = Session(HashMap.empty)
}