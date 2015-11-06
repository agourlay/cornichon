package com.github.agourlay.cornichon.core

import cats.data.Xor._
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.immutable.HashMap

class ResolverSpec extends WordSpec with Matchers {

  val resolver = Resolver.withoutExtractor()

  "Resolver" when {
    "fillPlaceholders" must {
      "replace a single string" in {
        val session = Session.newSession.addValue("project-name", "cornichon")
        val content = "This project is named <project-name>"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named cornichon"))
      }

      "not be confused by markup order" in {
        val session = Session.newSession.addValue("project-name", "cornichon")
        val content = "This project is named >project-name<"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named >project-name<"))
      }

      "not be confused if key contains empty string" in {
        val session = Session.newSession.addValue("project-name", "cornichon")
        val content = "This project is named <project name>"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named <project name>"))
      }

      "not be confused by unclosed markup used in a math context" in {
        val session = Session.newSession.addValue("pi", "3.14")
        val content = "3.15 > <pi>"
        resolver.fillPlaceholders(content)(session) should be(right("3.15 > 3.14"))
      }

      "return ResolverError if placeholder not found" in {
        val session = Session.newSession.addValue("project-name", "cornichon")
        val content = "This project is named <project-new-name>"
        resolver.fillPlaceholders(content)(session) should be(left(SimpleResolverError("project-new-name")))
      }

      "replace two strings" in {
        val session = Session.newSession.addValues(Seq("project-name" → "cornichon", "taste" → "tasty"))
        val content = "This project is named <project-name> and is super <taste>"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named cornichon and is super tasty"))
      }

      "return ResolverError for the first placeholder not found" in {
        val session = Session.newSession.addValues(Seq("project-name" → "cornichon", "taste" → "tasty"))
        val content = "This project is named <project-name> and is super <new-taste>"
        resolver.fillPlaceholders(content)(session) should be(left(SimpleResolverError("new-taste")))
      }

      "generate random-uuid" in {
        val session = Session.newSession
        val content = "<random-uuid>"
        resolver.fillPlaceholders(content)(session) should not be (right("<random-uuid>"))
      }
    }
  }
}
