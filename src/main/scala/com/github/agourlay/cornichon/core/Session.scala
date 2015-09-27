package com.github.agourlay.cornichon.core

import scala.collection.immutable.HashMap

case class Session(content: HashMap[String, String]) {

  def getOpt(key: String): Option[String] = content.get(key)
  def get(key: String): String = content.get(key).fold(throw new KeyNotFoundInSession(key, this))(identity)
  def getList(keys: Seq[String]) = keys.map(get)
  def addValue(key: String, value: String) = Session(content + (key → value))
  def addValues(tuples: Seq[(String, String)]) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))
  def removeKey(key: String) = Session(content - key)

  val prettyPrint = content.map(pair ⇒ pair._1 + " -> " + pair._2).mkString("\n")
}

object Session {
  def newSession = Session(HashMap.empty)
}