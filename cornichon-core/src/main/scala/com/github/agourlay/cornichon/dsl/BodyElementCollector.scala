package com.github.agourlay.cornichon.dsl

case class BodyElementCollector[Body, Result](fn: List[Body] => Result) extends AnyVal {
  def apply(body: => Body): Result = macro BodyElementCollectorMacro.collectImpl
  def apply(body: => Seq[Body]): Result = macro BodyElementCollectorMacro.collectImpl

  def get(body: List[Body]): Result = fn(body)
}