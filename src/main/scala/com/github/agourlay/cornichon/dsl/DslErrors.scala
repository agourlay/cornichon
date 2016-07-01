package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.CornichonError

sealed trait DslError extends CornichonError

case class DataTableError(error: Throwable, input: String) extends DslError {
  val msg = s"error thrown '${error.getMessage}' while parsing data table $input"
}

case class DataTableParseError(msg: String) extends DslError
