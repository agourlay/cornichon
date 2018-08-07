package com.github.agourlay.cornichon.core

import cats.data.NonEmptyList
import monix.eval.Task

package object core {
  type StepResult = Task[(RunState, FailedStep Either Done)]
}
