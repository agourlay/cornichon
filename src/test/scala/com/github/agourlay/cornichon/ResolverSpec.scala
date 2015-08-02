package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.Resolver
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.immutable.HashMap

import cats.data.Xor._

class ResolverSpec extends WordSpec with Matchers {

  val resolver = new Resolver

  "A resolver" must {
    "replace a single string" in {
      val source: Map[String, String] = new HashMap[String, String].+("project-name" -> "cornichon")
      val content = "This project is named <project-name>"
      resolver.fillPlaceHolder(content)(source) should be(right("This project is named cornichon"))
    }

    "replace two strings" in {
      val source: Map[String, String] = new HashMap[String, String].+("project-name" -> "cornichon", "taste" -> "tasty")
      val content = "This project is named <project-name> and is super <taste>"
      resolver.fillPlaceHolder(content)(source) should be(right("This project is named cornichon and is super tasty"))
    }
  }
}
