package com.github.agourlay.cornichon.core

import cats.data.StateT
import monix.eval.Task

package object core {
  type StepState = StateT[Task, RunState, FailedStep Either Done]
  type StepResult = Task[(RunState, FailedStep Either Done)]
}
