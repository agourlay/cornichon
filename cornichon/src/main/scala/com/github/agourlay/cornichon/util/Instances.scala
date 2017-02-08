package com.github.agourlay.cornichon.util

import cats.Show
import cats.instances.AllInstances
import cats.syntax.show._

import scala.collection.immutable.IndexedSeq

// Importing Cats instances for Show and Eq to make it easier for potential non dev-users.
trait Instances extends AllInstances {

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

object Instances extends Instances {
  def displayStringPairs(params: Seq[(String, String)]): String =
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")
}
