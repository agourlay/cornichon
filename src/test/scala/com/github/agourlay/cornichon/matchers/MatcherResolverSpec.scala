package com.github.agourlay.cornichon.matchers

import cats.scalatest.EitherValues
import com.github.agourlay.cornichon.core.SessionSpec._
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }

class MatcherResolverSpec extends WordSpec
    with Matchers
    with PropertyChecks
    with EitherValues {

  private val resolver = MatcherResolver()

  // Todo: add cases for interference of matchers and placeholders
  "MatcherResolver" when {
    "findMatchers" must {
      "find matcher in content solely containing a matcher without index" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"<<$key>>").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content starting with whitespace and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s" <<$key>>").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content starting with 2 whitespaces and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"  <<$key>>").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content finishing with whitespace and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"<<$key>> ").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content finishing with 2 whitespaces and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"<<$key>>  ").value should be(List(MatcherKey(key)))
        }
      }
    }
  }
}
