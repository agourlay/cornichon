package com.github.agourlay.cornichon.matchers

import cats.scalatest.EitherValues
import com.github.agourlay.cornichon.core.SessionSpec._
import org.scalatest.prop.PropertyChecks
import org.scalatest._

class MatcherResolverSpec extends WordSpec
  with Matchers
  with PropertyChecks
  with EitherValues {

  private val resolver = MatcherResolver()

  "MatcherResolver" when {
    "findMatchers" must {
      "find matcher in content solely containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"*$key*").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content starting with whitespace and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s" *$key*").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content starting with 2 whitespaces and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"  *$key*").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content finishing with whitespace and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"*$key* ").value should be(List(MatcherKey(key)))
        }
      }

      "find matcher in content finishing with 2 whitespaces and containing a matcher" in {
        forAll(keyGen) { key ⇒
          resolver.findMatcherKeys(s"*$key*  ").value should be(List(MatcherKey(key)))
        }
      }
    }
  }
}
