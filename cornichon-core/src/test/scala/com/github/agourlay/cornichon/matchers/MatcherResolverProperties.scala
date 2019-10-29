package com.github.agourlay.cornichon.matchers

import cats.syntax.either._
import com.github.agourlay.cornichon.core.SessionProperties._
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._
import org.typelevel.claimant.Claim

class MatcherResolverProperties extends Properties("MatcherResolver") {

  property("find matcher in content solely containing a matcher") = {
    forAll(keyGen) { key =>
      Claim {
        MatcherResolver.findMatcherKeys(s"*$key*") == Right(List(MatcherKey(key)))
      }
    }
  }

  property("find matcher in content starting with whitespace and containing a matcher") = {
    forAll(keyGen) { key =>
      Claim {
        MatcherResolver.findMatcherKeys(s" *$key*") == Right(List(MatcherKey(key)))
      }
    }
  }

  property("find matcher in content starting with 2 whitespaces and containing a matcher") = {
    forAll(keyGen) { key =>
      Claim {
        MatcherResolver.findMatcherKeys(s"  *$key*") == Right(List(MatcherKey(key)))
      }
    }
  }

  property("find matcher in content finishing with whitespace and containing a matcher") = {
    forAll(keyGen) { key =>
      Claim {
        MatcherResolver.findMatcherKeys(s"*$key* ") == Right(List(MatcherKey(key)))
      }
    }
  }

  property("find matcher in content finishing with 2 whitespaces and containing a matcher") = {
    forAll(keyGen) { key =>
      Claim {
        MatcherResolver.findMatcherKeys(s"*$key*  ") == Right(List(MatcherKey(key)))
      }
    }
  }

  property("resolveMatcherKeys detect duplicate matchers") = {
    val allMatchers = MatcherResolver.builtInMatchers
    forAll(Gen.oneOf(allMatchers)) { m =>
      val input = (m :: m :: Nil).groupBy(_.key)
      val expected = Left(s"there are 2 matchers named '${m.key}': '${m.description}' and '${m.description}'")
      Claim {
        MatcherResolver.resolveMatcherKeys(input)(MatcherKey(m.key)).leftMap(_.renderedMessage) == expected
      }
    }
  }
}
