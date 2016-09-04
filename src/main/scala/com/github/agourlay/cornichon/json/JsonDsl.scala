package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.json.JsonAssertions._

trait JsonDsl {
  this: CornichonFeature ⇒

  val root = JsonPath.root

  def session_json_values(k1: String, k2: String) = JsonValuesAssertion(k1, k2, resolver)

}
