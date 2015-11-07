package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.json.CornichonJson

import scala.collection.immutable.HashMap
import scala.util._

case class Session(content: HashMap[String, Vector[String]]) {

  val json = new CornichonJson()

  def getOpt(key: String): Option[String] =
    parseStackedKey(key).fold[Option[String]] {
      content.get(key).map(values ⇒ values.last)
    } { stackedKey ⇒
      content.get(stackedKey.keyName).map(values ⇒ values(stackedKey.indice))
    }

  def get(key: String): String = getOpt(key).getOrElse(throw new KeyNotFoundInSession(key, this))

  def getJson(key: String) = json.parseJson(get(key))

  def getList(keys: Seq[String]) = keys.map(get)

  def addValue(key: String, value: String) =
    content.get(key).fold(Session(content + (key → Vector(value)))) { values ⇒
      Session((content - key) + (key → values.:+(value)))
    }

  def addValues(tuples: Seq[(String, String)]) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))

  def removeKey(key: String) = Session(content - key)

  val prettyPrint = content.toSeq.sortBy(_._1).map(pair ⇒ pair._1 + " -> " + pair._2).mkString("\n")

  def parseStackedKey(key: String): Option[StackedKey] =
    parseIndice(key).flatMap { indiceStr ⇒
      Try { indiceStr.toInt } match {
        case Failure(_) ⇒ None
        case Success(index) ⇒
          val keyName = key.dropRight(indiceStr.length + 2)
          Some(StackedKey(keyName, index))
      }
    }

  def parseIndice(key: String): Option[String] = {
    key.reverse.headOption.flatMap { head ⇒
      if (head == ']')
        Some(key.reverse.drop(1).takeWhile(_ != '[').reverse)
      else
        None
    }
  }

  case class StackedKey(keyName: String, indice: Int)
}

object Session {
  def newSession = Session(HashMap.empty)
}