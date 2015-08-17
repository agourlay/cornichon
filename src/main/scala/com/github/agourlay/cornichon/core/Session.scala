package com.github.agourlay.cornichon.core

import scala.collection.immutable.HashMap

case class Session(content: HashMap[String, String]) {

  def getKey(key: String): Option[String] = content.get(key)
  def addValue(key: String, value: String) = Session(content + (key → value))
  def removeKey(key: String) = Session(content - key)
}

object Session {
  def newSession = Session(HashMap.empty)
}