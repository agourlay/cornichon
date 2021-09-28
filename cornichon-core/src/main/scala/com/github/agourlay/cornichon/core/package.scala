package com.github.agourlay.cornichon

import cats.data.StateT
import cats.effect.IO

package object core {
  type StepState = StateT[IO, RunState, FailedStep Either Done]
  type StepResult = IO[(RunState, FailedStep Either Done)]
}
