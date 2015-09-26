package com.github.agourlay.cornichon.core

import scala.collection.immutable.HashMap

case class Session(content: HashMap[String, String]) {

  def getOpt(key: String): Option[String] = content.get(key)
  def get(key: String): String = content.get(key).fold(throw new KeyNotFoundInSession(key))(identity)
  def addValue(key: String, value: String) = Session(content + (key → value))
  def removeKey(key: String) = Session(content - key)
}

object Session {
  def newSession = Session(HashMap.empty)
}