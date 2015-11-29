package com.github.agourlay.cornichon.core

import cats.data.Xor._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ OptionValues, Matchers, WordSpec }
import SessionSpec._

class ResolverSpec extends WordSpec with Matchers with OptionValues with PropertyChecks {

  val resolver = Resolver.withoutExtractor()

  "Resolver" when {
    "findPlaceholders" must {
      "find placeholder in content solely containing a placeholder without index" in {
        resolver.findPlaceholders("<test>") should be(List(Placeholder("test", None)))
      }

      "find placeholder in content solely containing a placeholder with index" in {
        resolver.findPlaceholders("<test[1]>") should be(List(Placeholder("test", Some(1))))
      }

      "find placeholder in content starting with whitespace and containing a placeholder" in {
        resolver.findPlaceholders(" <test>") should be(List(Placeholder("test", None)))
      }

      "find placeholder in content starting with 2 whitespaces and containing a placeholder" in {
        resolver.findPlaceholders("  <test>") should be(List(Placeholder("test", None)))
      }

      "find placeholder in content finishing with whitespace and containing a placeholder" in {
        resolver.findPlaceholders("<test> ") should be(List(Placeholder("test", None)))
      }

      "find placeholder in content finishing with 2 whitespaces and containing a placeholder" in {
        resolver.findPlaceholders("<test>  ") should be(List(Placeholder("test", None)))
      }
    }

    "fillPlaceholders" must {
      "replace a single string" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newSession.addValue(ph, value)
          val content = s"This project is <$ph>"
          resolver.fillPlaceholders(content)(session) should be(right(s"This project is $value"))
        }
      }

      "not be confused by markup order" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newSession.addValue(ph, value)
          val content = s"This project is >$ph<"
          resolver.fillPlaceholders(content)(session) should be(right(s"This project is >$ph<"))
        }
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
        resolver.fillPlaceholders(content)(session) should be(left(SimpleResolverError("project-new-name", session)))
      }

      "replace two strings" in {
        val session = Session.newSession.addValues(Seq("project-name" → "cornichon", "taste" → "tasty"))
        val content = "This project is named <project-name> and is super <taste>"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named cornichon and is super tasty"))
      }

      "return ResolverError for the first placeholder not found" in {
        val session = Session.newSession.addValues(Seq("project-name" → "cornichon", "taste" → "tasty"))
        val content = "This project is named <project-name> and is super <new-taste>"
        resolver.fillPlaceholders(content)(session) should be(left(SimpleResolverError("new-taste", session)))
      }

      "generate random-uuid" in {
        val session = Session.newSession
        val content = "<random-uuid>"
        resolver.fillPlaceholders(content)(session) should not be right("<random-uuid>")
      }

      "stacked key with indice zero always takes the first value in session" in {
        val s = Session.newSession.addValue("one", "v1").addValue("one", "v2")
        val content = "<one[0]>"
        resolver.fillPlaceholders(content)(s) should be(right("v1"))
      }

      "stacked key with indice one always takes the second value in session" in {
        val s = Session.newSession.addValue("one", "v1").addValue("one", "v2")
        val content = "<one[1]>"
        resolver.fillPlaceholders(content)(s) should be(right("v2"))
      }
    }
  }
}
