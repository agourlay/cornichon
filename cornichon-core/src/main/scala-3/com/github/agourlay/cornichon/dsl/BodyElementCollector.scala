package com.github.agourlay.cornichon.dsl

final case class BodyElementCollector[Body, Result](fn: List[Body] => Result) extends AnyVal {

  inline def apply(inline body: => Any): Result =
   ${ BodyElementCollectorMacro.collectOneImpl[Body, Result]('body, 'fn) }

  def get(body: List[Body]): Result = fn(body)
}
