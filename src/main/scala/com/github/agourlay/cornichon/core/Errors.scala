package com.github.agourlay.cornichon.core

trait CornichonError {
  val msg: String
}

case class StepExecutionError[A](title: String, exception: Throwable) extends CornichonError {
  val msg = s"step '$title' failed by throwing exception ${exception.printStackTrace()}"
}

case class StepAssertionError[A](title: String, result: A) extends CornichonError {
  val msg = s"step '$title' did not pass assertion, actual result is '$result'"
}

case class StepPredicateError[A](title: String, exception: Throwable) extends CornichonError {
  val msg = s"step '$title' predicate failed by throwing exception ${exception.printStackTrace()}"
}

case class ResolverError(key: String) extends CornichonError {
  val msg = s"key '<$key>' can not be resolved"
}

case class SessionError(title: String, key: String) extends CornichonError {
  val msg = s"key '$key' can not be found session for step '$title'"
}

case class KeyNotFoundInSession(key: String) extends Exception