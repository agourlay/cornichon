package com.github.agourlay.cornichon.resolver

import com.github.agourlay.cornichon.core.Session

sealed trait Mapper

case class SimpleMapper(generator: () ⇒ String) extends Mapper

object SimpleMapper {
  implicit def fromFct(generator: () ⇒ String): SimpleMapper = SimpleMapper(generator)
}

case class SessionMapper(generator: Session ⇒ String) extends Mapper

case class TextMapper(key: String, transform: String ⇒ String = identity) extends Mapper

case class HistoryMapper(key: String, transform: Vector[String] ⇒ String) extends Mapper

case class JsonMapper(key: String, jsonPath: String, transform: String ⇒ String = identity) extends Mapper