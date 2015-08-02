package com.github.agourlay.cornichon.core

case class Step[A](title: String, instruction: Session ⇒ (A, Session), assertion: A ⇒ Boolean)