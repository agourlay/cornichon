package com.github.agourlay.cornichon.dsl

import cats.instances._

// Importing by default Cats instances for common types to make it easier for potential non dev-users.
// Built-in DSL/assertions work with Show, Eq and Order
trait ProvidedInstances extends StringInstances
  with IntInstances
  with CharInstances
  with LongInstances
  with FloatInstances
  with DoubleInstances
  with BooleanInstances

object ProvidedInstances extends ProvidedInstances