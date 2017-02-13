package com.github.agourlay.cornichon.dsl

import cats.instances._

trait AllInstances
  extends Instances
    with StringInstances
    with ListInstances
    with OptionInstances
    with SetInstances
    with VectorInstances
    with IntInstances
    with CharInstances
    with LongInstances
    with ShortInstances
    with FloatInstances
    with DoubleInstances
    with BooleanInstances
    with BigIntInstances
    with BigDecimalInstances
    with UUIDInstances

object AllInstances
