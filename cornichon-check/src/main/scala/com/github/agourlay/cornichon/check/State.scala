package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep

case class Model(states: List[State])

case class State(
    preConditions: List[AssertStep],
    action: EffectStep,
    postConditions: List[AssertStep],
    transitions: List[Transition])

case class Transition(weight: Double, state: State)
