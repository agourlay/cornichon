package com.github.agourlay.cornichon.core

import monix.eval.Task

package object core {
  type StepResult = Task[(RunState, FailedStep Either Done)]
}
