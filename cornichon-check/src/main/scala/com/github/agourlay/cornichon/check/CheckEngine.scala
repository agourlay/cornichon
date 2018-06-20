package com.github.agourlay.cornichon.check

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core.{ CornichonError, Done, RunState }
import monix.eval.Task

object CheckEngine {

  def run(initialRunState: RunState, model: Model): Task[Either[NonEmptyList[CornichonError], Done]] = {
//    model.states.map{ s =>
//      val preCond = s.preConditions.map(_.run(initialRunState))
//      Task.gather(preCond).flatMap{ preConditions =>
//        preConditions.collect{case Right(e) => e}
//        preConditions.collect{case Left(e) => e}
//      }
//    }

    Task.delay {
      println(s"Check engine with initialState\n$initialRunState and model $model")
      Right(Done)
    }
  }

}
