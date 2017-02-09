package com.github.agourlay.cornichon.dsl

import cats.Show
import cats.instances._
import cats.syntax.show._

import scala.collection.immutable.IndexedSeq

// Importing Cats instances for Show and Eq to make it easier for potential non dev-users.
trait ProvidedInstances extends StringInstances
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
    with UUIDInstances {

  implicit def showSeq[A: Show]: Show[Seq[A]] = Show.show { fa ⇒
    fa.toIterator.map(_.show).mkString("Seq(", ", ", ")")
  }

  implicit def showIterable[A: Show]: Show[Iterable[A]] = Show.show { fa ⇒
    fa.toIterator.map(_.show).mkString("(", ", ", ")")
  }

  implicit def showIndexedSeq[A: Show]: Show[IndexedSeq[A]] = Show.show { fa ⇒
    fa.toIterator.map(_.show).mkString("IndexedSeq(", ", ", ")")
  }

  implicit def showMap[A: Show: Ordering, B: Show]: Show[Map[A, B]] = Show.show { ma ⇒
    ma.toSeq.sortBy(_._1).map(pair ⇒ pair._1.show + " -> " + pair._2.show).mkString("\n")
  }
}

object ProvidedInstances extends ProvidedInstances