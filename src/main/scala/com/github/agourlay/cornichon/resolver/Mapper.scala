package com.github.agourlay.cornichon.resolver

import org.scalacheck.Gen

sealed trait Mapper

case class SimpleMapper(generator: () ⇒ String) extends Mapper

object SimpleMapper {
  implicit def fromFct(generator: () ⇒ String): SimpleMapper = SimpleMapper(generator)
}

case class GenMapper(gen: Gen[String]) extends Mapper

object GenMapper {
  implicit def fromGen(generator: Gen[String]): GenMapper = GenMapper(generator)
}

case class TextMapper(key: String, transform: String ⇒ String = identity) extends Mapper

case class JsonMapper(key: String, jsonPath: String, transform: String ⇒ String = identity) extends Mapper