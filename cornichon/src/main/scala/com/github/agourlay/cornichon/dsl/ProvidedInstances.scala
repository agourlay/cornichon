package com.github.agourlay.cornichon.dsl

import cats.Show
import cats.instances._
import cats.syntax.show._

// Importing by default Cats instances for common types to make it easier for potential non dev-users.
// Built-in DSL/assertions work with Show, Eq and Order
trait ProvidedInstances extends StringInstances
    with IntInstances
    with CharInstances
    with LongInstances
    with FloatInstances
    with DoubleInstances
    with BooleanInstances {

  implicit def showSeq[A: Show]: Show[Seq[A]] = Show.show { fa ⇒
    fa.toIterator.map(_.show).mkString("Seq(", ", ", ")")
  }

  implicit def showIterable[A: Show]: Show[Iterable[A]] = Show.show { fa ⇒
    fa.toIterator.map(_.show).mkString("(", ", ", ")")
  }

  implicit def showMap[A: Show: Ordering, B: Show]: Show[Map[A, B]] = Show.show { ma ⇒
    ma.toSeq.sortBy(_._1).map(pair ⇒ pair._1.show + " -> " + pair._2.show).mkString("\n")
  }
}

object ProvidedInstances extends ProvidedInstances