package com.github.agourlay.cornichon.dsl

object SessionAssertionErrors {

  def keyIsPresentError(keyName: String): Option[String] ⇒ String = resOpt ⇒ {
    s"""expected key '$keyName' to be absent from session but it was found with value :
        |${resOpt.get}""".stripMargin
  }

  def keyIsAbsentError(keyName: String, session: String): Boolean ⇒ String = resFalse ⇒ {
    s"""expected key '$keyName' to be present but it was not found in the session :
        |$session""".stripMargin
  }

}
