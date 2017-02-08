package com.github.agourlay.cornichon.core

import cats.syntax.either._
import com.github.agourlay.cornichon.resolver.Resolver

sealed trait StepPreparer {

  def run(session: Session)(step: Step): CornichonError Either Step

}

case class StepPreparerTitleResolver(resolver: Resolver) extends StepPreparer {
  def run(session: Session)(step: Step) = resolver.fillPlaceholders(step.title)(session).map(step.setTitle)
}

