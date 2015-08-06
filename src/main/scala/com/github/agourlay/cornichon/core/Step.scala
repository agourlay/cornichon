package com.github.agourlay.cornichon.core

case class Step[A](title: String, action: Session ⇒ (A, Session), expected: A)

case class StepAssertionResult[A](result: Boolean, expected: A, actual: A)
