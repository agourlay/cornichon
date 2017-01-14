package com.github.agourlay.cornichon.resolver

sealed trait Mapper

case class SimpleMapper(generator: () ⇒ String) extends Mapper

object SimpleMapper {
  implicit def fromFct(generator: () ⇒ String): SimpleMapper = SimpleMapper(generator)
}

case class TextMapper(key: String, transform: String ⇒ String = identity) extends Mapper

case class JsonMapper(key: String, jsonPath: String, transform: String ⇒ String = identity) extends Mapper