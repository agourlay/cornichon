package com.github.agourlay.cornichon.core

import cats.data.Xor._
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.immutable.HashMap

class ResolverSpec extends WordSpec with Matchers {

  val resolver = Resolver

  "A resolver" must {
    "replace a single string" in {
      val source = new HashMap[String, String].+("project-name" → "cornichon")
      val content = "This project is named <project-name>"
      resolver.fillPlaceholder(content)(source) should be(right("This project is named cornichon"))
    }

    "not be confused by markup order" in {
      val source = new HashMap[String, String].+("project-name" → "cornichon")
      val content = "This project is named >project-name<"
      resolver.fillPlaceholder(content)(source) should be(right("This project is named >project-name<"))
    }

    "not be confused if key contains emtpy string" in {
      val source = new HashMap[String, String].+("project-name" → "cornichon")
      val content = "This project is named <project name>"
      resolver.fillPlaceholder(content)(source) should be(right("This project is named <project name>"))
    }

    "return ResolverError if placeholder not found" in {
      val source = new HashMap[String, String].+("project-name" → "cornichon")
      val content = "This project is named <project-new-name>"
      resolver.fillPlaceholder(content)(source) should be(left(ResolverError("project-new-name")))
    }

    "replace two strings" in {
      val source = new HashMap[String, String].+("project-name" → "cornichon", "taste" → "tasty")
      val content = "This project is named <project-name> and is super <taste>"
      resolver.fillPlaceholder(content)(source) should be(right("This project is named cornichon and is super tasty"))
    }

    "return ResolverError for the first placeholder not found" in {
      val source = new HashMap[String, String].+("project-name" → "cornichon", "taste" → "tasty")
      val content = "This project is named <project-name> and is super <new-taste>"
      resolver.fillPlaceholder(content)(source) should be(left(ResolverError("new-taste")))
    }
  }
}
