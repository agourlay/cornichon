package com.github.agourlay.cornichon.resolver

import com.github.agourlay.cornichon.core.{ CornichonError, RandomContext, Session }

sealed trait Mapper

case class SimpleMapper(generator: () => String) extends Mapper

case class RandomMapper(generator: RandomContext => String) extends Mapper

case class SessionMapper(generator: Session => Either[CornichonError, String]) extends Mapper

case class TextMapper(key: String, transform: String => String = identity) extends Mapper

case class HistoryMapper(key: String, transform: Vector[String] => String) extends Mapper

case class JsonMapper(key: String, jsonPath: String, transform: String => String = identity) extends Mapper