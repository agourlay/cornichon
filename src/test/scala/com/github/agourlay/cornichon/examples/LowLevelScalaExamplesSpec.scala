package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature

class LowLevelScalaExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    feature("Low level Scala Dsl test") {
      scenario("test scenario")(
        Given("A value") { s ⇒
          val x = 33 + 33
          val s2 = s.addValue("my-key", "crazy value")
          val s3 = s2.addValue("name-in-title", "String")
          (x, s3)
        }(66),
        When("I take a letter <name-in-title>") { s ⇒
          val x = 'A'
          (x, s)
        }('A'),
        When("I check the session") { s ⇒
          val x = s.getKey("my-key")
          (x, s)
        }(Some("crazy value"))
      )
    }
}
