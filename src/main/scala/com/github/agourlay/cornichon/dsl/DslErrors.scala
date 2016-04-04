package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.CornichonError

sealed trait DslError extends CornichonError

case class DataTableError(error: Throwable) extends DslError {
  val msg = s"error thrown while parsing data table ${error.getMessage}"
}

case class DataTableParseError(msg: String) extends DslError
