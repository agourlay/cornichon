package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.CornichonError

sealed trait DslError extends CornichonError

case class DataTableError(error: Throwable) extends DslError {
  val msg = s"error thrown while parsing data table ${error.getMessage}"
}

case class DataTableParseError(msg: String) extends DslError

case object MalformedConcurrentBlock extends DslError {
  val msg = "malformed concurrent block without closing 'ConcurrentlyStop'"
}

case object MalformedEventuallyBlock extends DslError {
  val msg = "malformed eventually block without closing 'EventuallyStop'"
}

case object ConcurrentlyTimeout extends DslError {
  val msg = "concurrent block did not reach completion in 'maxTime'"
}

case object EventuallyBlockSucceedAfterMaxDuration extends DslError {
  val msg = "eventually block succeeded after 'maxDuration'"
}

case object WithinBlockSucceedAfterMaxDuration extends DslError {
  val msg = "within block succeeded after 'maxDuration'"
}
