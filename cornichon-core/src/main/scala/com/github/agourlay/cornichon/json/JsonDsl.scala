package com.github.agourlay.cornichon.json

import cats.syntax.show._
import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.dsl.CoreDsl
import com.github.agourlay.cornichon.json.CornichonJson._

trait JsonDsl {
  this: CoreDsl =>

  def show_key_as_json(key: String, index: Option[Int] = None): Step =
    show_session(key, index, v => parseString(v).map(_.show))
}
